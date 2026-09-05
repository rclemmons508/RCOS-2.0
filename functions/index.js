const functions = require('firebase-functions');
const admin = require('firebase-admin');
const { GoogleGenerativeAI } = require('@google/generative-ai');

admin.initializeApp();
const db = admin.firestore();
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);

// ============================================
// Cloud Function: Spawn Agent
// ============================================
exports.spawnAgent = functions.https.onCall(async (data, context) => {
  // Verify authentication
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'User must be authenticated'
    );
  }

  const userId = context.auth.uid;
  const {
    agentId,
    agentType,
    industry,
    bottleneck,
    tools = [],
    customInstructions = ''
  } = data;

  try {
    // Build agent prompt
    const systemPrompt = `You are an autonomous agent for the ${industry} industry.
    
Your Role: Help solve this business bottleneck: "${bottleneck}"

Available Tools:
${tools.map((t, i) => `${i + 1}. ${t}`).join('\n')}

Custom Instructions:
${customInstructions || 'None'}

Respond with:
1. Initial analysis of the problem
2. Step-by-step action plan
3. Expected outcomes

Be professional and actionable.`;

    // Call Gemini API
    const model = genAI.getGenerativeModel({ model: 'gemini-2.0-flash' });
    const result = await model.generateContent(systemPrompt);
    const responseText = result.response.text();

    // Save agent execution to Firestore
    const agentExecRef = db.collection('users').doc(userId)
      .collection('agents').doc(agentId);

    await agentExecRef.set({
      type: agentType,
      industry,
      bottleneck,
      tools,
      customInstructions,
      response: responseText,
      status: 'completed',
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      executionTime: new Date().getTime()
    });

    // Log to audit trail
    await db.collection('users').doc(userId)
      .collection('auditLogs').add({
        action: 'AGENT_SPAWNED',
        agentId,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        details: { industry, bottleneck }
      });

    return {
      success: true,
      agentId,
      response: responseText,
      executedAt: new Date().toISOString()
    };
  } catch (error) {
    console.error('Error spawning agent:', error);
    throw new functions.https.HttpsError(
      'internal',
      'Failed to spawn agent: ' + error.message
    );
  }
});

// ============================================
// Cloud Function: Execute Workflow
// ============================================
exports.executeWorkflow = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'User must be authenticated'
    );
  }

  const userId = context.auth.uid;
  const { workflowId, agentConfigs = [] } = data;

  try {
    // Create job document
    const jobRef = db.collection('users').doc(userId)
      .collection('jobs').doc();

    await jobRef.set({
      workflowId,
      status: 'running',
      progress: 0,
      agentCount: agentConfigs.length,
      completedAgents: 0,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      results: []
    });

    // Execute agents in parallel
    const agentPromises = agentConfigs.map(config =>
      executeAgentInWorkflow(userId, jobRef, config)
    );

    await Promise.all(agentPromises);

    // Mark job as complete
    const results = await db.collection('users').doc(userId)
      .collection('jobs').doc(jobRef.id).get();

    await jobRef.update({
      status: 'completed',
      completedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    return {
      success: true,
      jobId: jobRef.id,
      message: 'Workflow executed successfully'
    };
  } catch (error) {
    console.error('Error executing workflow:', error);
    throw new functions.https.HttpsError(
      'internal',
      'Failed to execute workflow: ' + error.message
    );
  }
});

// Helper: Execute single agent in workflow
async function executeAgentInWorkflow(userId, jobRef, config) {
  try {
    const model = genAI.getGenerativeModel({ model: 'gemini-2.0-flash' });
    const result = await model.generateContent(config.prompt);

    await jobRef.update({
      results: admin.firestore.FieldValue.arrayUnion({
        agentType: config.type,
        result: result.response.text(),
        timestamp: new Date().toISOString()
      }),
      completedAgents: admin.firestore.FieldValue.increment(1)
    });
  } catch (error) {
    console.error('Agent execution error:', error);
    throw error;
  }
}

// ============================================
// Cloud Function: Get Job Status
// ============================================
exports.getJobStatus = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'User must be authenticated'
    );
  }

  const userId = context.auth.uid;
  const { jobId } = data;

  try {
    const jobDoc = await db.collection('users').doc(userId)
      .collection('jobs').doc(jobId).get();

    if (!jobDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Job not found');
    }

    return {
      jobId,
      ...jobDoc.data()
    };
  } catch (error) {
    console.error('Error getting job status:', error);
    throw new functions.https.HttpsError(
      'internal',
      'Failed to get job status: ' + error.message
    );
  }
});

// ============================================
// Cloud Function: Deep Reasoning (Extended Thinking)
// ============================================
exports.deepReasoning = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'User must be authenticated'
    );
  }

  const userId = context.auth.uid;
  const { problem, context: problemContext = '' } = data;

  try {
    const model = genAI.getGenerativeModel({
      model: 'gemini-2.0-flash',
      systemInstruction: 'You are an expert problem solver. Analyze deeply and provide thorough reasoning.'
    });

    const prompt = `${problemContext ? problemContext + '\n\n' : ''}Problem: ${problem}`;
    const result = await model.generateContent(prompt);

    // Save to reasoning history
    await db.collection('users').doc(userId)
      .collection('reasoning').add({
        problem,
        context: problemContext,
        result: result.response.text(),
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });

    return {
      success: true,
      reasoning: result.response.text(),
      timestamp: new Date().toISOString()
    };
  } catch (error) {
    console.error('Error in deep reasoning:', error);
    throw new functions.https.HttpsError(
      'internal',
      'Failed to perform reasoning: ' + error.message
    );
  }
});

// ============================================
// Scheduled Function: Cleanup Old Jobs
// ============================================
exports.cleanupOldJobs = functions.pubsub
  .schedule('every 24 hours')
  .onRun(async (context) => {
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

    const snapshot = await db.collectionGroup('jobs')
      .where('createdAt', '<', thirtyDaysAgo)
      .limit(100)
      .get();

    const batch = db.batch();
    snapshot.docs.forEach(doc => batch.delete(doc.ref));
    await batch.commit();

    console.log(`Cleaned up ${snapshot.docs.length} old jobs`);
    return null;
  });
