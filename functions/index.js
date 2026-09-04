const functions = require('firebase-functions');
const admin = require('firebase-admin');
const { GoogleGenerativeAI } = require('@google/generative-ai');

admin.initializeApp();
const db = admin.firestore();

// Initialize Gemini with environment variable
const apiKey = process.env.GEMINI_API_KEY;
const genAI = apiKey ? new GoogleGenerativeAI(apiKey) : null;

/**
 * Cloud Function: Spawn Agent for Workflow
 * Executes an autonomous agent task using Gemini API
 */
exports.spawnAgent = functions.https.onCall(async (data, context) => {
  // Verify user is authenticated
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'User must be authenticated'
    );
  }

  if (!genAI) {
    throw new functions.https.HttpsError(
      'failed-precondition',
      'Gemini API is not configured. Set GEMINI_API_KEY environment variable.'
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
    // Build agent system prompt
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
4. Success metrics

Be professional, actionable, and concise.`;

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
      executionTime: Date.now()
    });

    // Log to audit trail
    await db.collection('users').doc(userId)
      .collection('auditLogs').add({
        action: 'AGENT_SPAWNED',
        agentId,
        agentType,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        details: { industry, bottleneck }
      });

    console.log(`Agent ${agentId} spawned successfully for user ${userId}`);

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

/**
 * Cloud Function: Execute Workflow
 * Runs multiple agents in sequence or parallel
 */
exports.executeWorkflow = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'User must be authenticated'
    );
  }

  if (!genAI) {
    throw new functions.https.HttpsError(
      'failed-precondition',
      'Gemini API is not configured.'
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
      executeAgentInWorkflow(userId, jobRef.id, config)
    );

    const results = await Promise.allSettled(agentPromises);

    // Mark job as complete
    const completedCount = results.filter(r => r.status === 'fulfilled').length;
    
    await jobRef.update({
      status: completedCount === agentConfigs.length ? 'completed' : 'partial_failure',
      completedAt: admin.firestore.FieldValue.serverTimestamp(),
      completedAgents: completedCount
    });

    console.log(`Workflow ${workflowId} executed with ${completedCount}/${agentConfigs.length} agents succeeding`);

    return {
      success: completedCount === agentConfigs.length,
      jobId: jobRef.id,
      completedAgents: completedCount,
      totalAgents: agentConfigs.length,
      message: `Workflow executed: ${completedCount}/${agentConfigs.length} agents completed`
    };
  } catch (error) {
    console.error('Error executing workflow:', error);
    throw new functions.https.HttpsError(
      'internal',
      'Failed to execute workflow: ' + error.message
    );
  }
});

/**
 * Helper: Execute single agent in workflow
 */
async function executeAgentInWorkflow(userId, jobId, config) {
  try {
    const model = genAI.getGenerativeModel({ model: 'gemini-2.0-flash' });
    const result = await model.generateContent(config.prompt);

    const jobRef = db.collection('users').doc(userId)
      .collection('jobs').doc(jobId);

    await jobRef.update({
      results: admin.firestore.FieldValue.arrayUnion({
        agentType: config.type,
        result: result.response.text(),
        timestamp: new Date().toISOString()
      }),
      completedAgents: admin.firestore.FieldValue.increment(1)
    });

    return { success: true };
  } catch (error) {
    console.error('Agent execution error:', error);
    throw error;
  }
}

/**
 * Cloud Function: Get Job Status
 * Retrieve the current status of a job
 */
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

/**
 * Cloud Function: Deep Reasoning
 * Uses extended thinking for complex problem solving
 */
exports.deepReasoning = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'User must be authenticated'
    );
  }

  if (!genAI) {
    throw new functions.https.HttpsError(
      'failed-precondition',
      'Gemini API is not configured.'
    );
  }

  const userId = context.auth.uid;
  const { problem, problemContext = '' } = data;

  try {
    const model = genAI.getGenerativeModel({
      model: 'gemini-2.0-flash',
      systemInstruction: 'You are an expert problem solver. Analyze deeply and provide thorough reasoning with actionable conclusions.'
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

/**
 * Scheduled Function: Cleanup Old Jobs
 * Automatically removes jobs older than 30 days
 */
exports.cleanupOldJobs = functions.pubsub
  .schedule('every 24 hours')
  .onRun(async (context) => {
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

    try {
      const snapshot = await db.collectionGroup('jobs')
        .where('createdAt', '<', thirtyDaysAgo)
        .limit(100)
        .get();

      const batch = db.batch();
      snapshot.docs.forEach(doc => batch.delete(doc.ref));
      await batch.commit();

      console.log(`Cleanup: Deleted ${snapshot.docs.length} old jobs`);
      return null;
    } catch (error) {
      console.error('Cleanup error:', error);
      return null;
    }
  });

/**
 * Scheduled Function: Archive Completed Tasks
 * Moves completed jobs to archive collection
 */
exports.archiveCompletedTasks = functions.pubsub
  .schedule('every 6 hours')
  .onRun(async (context) => {
    try {
      const snapshot = await db.collectionGroup('jobs')
        .where('status', '==', 'completed')
        .limit(50)
        .get();

      const batch = db.batch();
      snapshot.docs.forEach(doc => {
        const data = doc.data();
        // Archive the document
        batch.set(doc.ref.parent.parent.collection('archivedJobs').doc(doc.id), data);
        // Remove from active jobs
        batch.delete(doc.ref);
      });
      await batch.commit();

      console.log(`Archived ${snapshot.docs.length} completed tasks`);
      return null;
    } catch (error) {
      console.error('Archive error:', error);
      return null;
    }
  });
