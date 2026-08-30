# Cloud Functions Backend Setup

This guide sets up the Node.js Cloud Functions that power agent orchestration.

## Overview

The backend provides:
- Agent spawning and orchestration
- Workflow execution
- Job status tracking
- Gemini API integration
- Firestore data persistence

## Prerequisites

- Firebase project created (see SETUP_GUIDE.md)
- Node.js 16+ installed
- Firebase CLI installed

```bash
# Install Firebase CLI
npm install -g firebase-tools

# Login to Firebase
firebase login

# Verify installation
firebase --version
```

## Step 1: Initialize Cloud Functions

```bash
# From RCOS-2.0 directory
cd RCOS-2.0

# Initialize Firebase in project
firebase init

# Select options:
# Which Firebase features? → Functions (space to select)
# Select default Firebase project → RCOS-2.0
# JavaScript or TypeScript? → JavaScript
# ESLint? → No
# Install dependencies? → Yes
```

This creates:
```
functions/
  ├── .eslintrc.js
  ├── index.js          (main functions file)
  ├── package.json
  └── node_modules/
```

## Step 2: Create Cloud Functions

Replace `functions/index.js` with:

```javascript
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
```

## Step 3: Install Dependencies

```bash
cd functions
npm install
```

Update `functions/package.json` to include:

```json
{
  "name": "functions",
  "description": "Cloud Functions for RCOS",
  "scripts": {
    "serve": "firebase emulators:start --only functions",
    "deploy": "firebase deploy --only functions",
    "logs": "firebase functions:log"
  },
  "engines": {
    "node": "18"
  },
  "dependencies": {
    "firebase-admin": "^12.0.0",
    "firebase-functions": "^5.0.0",
    "@google/generative-ai": "^0.1.0"
  }
}
```

```bash
# Install new dependencies
npm install @google/generative-ai
```

## Step 4: Test Locally

```bash
# Start Firebase Emulator
firebase emulators:start --only functions

# You'll see:
# ✔  functions: Local server running at http://localhost:5001

# In another terminal, test a function (example):
curl -X POST http://localhost:5001/rclemmons508-rcos-2-0/us-central1/spawnAgent \
  -H "Content-Type: application/json" \
  -d '{"agentId":"test-agent","industry":"Tech","bottleneck":"Support tickets"}'
```

## Step 5: Deploy to Firebase

```bash
# From RCOS-2.0 directory
firebase deploy --only functions

# You'll see deployment status and URLs
# Functions are now live!
```

## Step 6: Set Environment Variables

Cloud Functions need the Gemini API key:

```bash
# Set environment variable
firebase functions:config:set gemini.api_key="your_key_here"

# Or set it in functions/index.js:
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || 'your_key_here';
```

## Step 7: Verify Deployment

```bash
# View function logs
firebase functions:log

# List deployed functions
firebase functions:list

# Delete a function if needed
firebase functions:delete spawnAgent
```

## Using Functions from Android App

The app calls functions using Firebase SDK. Example from `NovaViewModel.kt`:

```kotlin
private val functions = Firebase.functions

fun spawnAgent(agentId: String, industry: String, bottleneck: String) {
    functions.getHttpsCallable("spawnAgent")
        .call(mapOf(
            "agentId" to agentId,
            "industry" to industry,
            "bottleneck" to bottleneck
        ))
        .addOnSuccessListener { result ->
            val response = result.data as? Map<*, *>
            Log.d("Agent", "Success: ${response?.get("response")}")
        }
        .addOnFailureListener { error ->
            Log.e("Agent", "Error: ${error.message}")
        }
}
```

## Troubleshooting

### Functions won't deploy
```bash
# Check function syntax
./gradlew lintKotlin

# Verify Firebase config
firebase projects:list

# Check project default
firebase use RCOS-2.0
```

### Gemini API errors in functions
```bash
# Verify API key is set
firebase functions:config:get

# View function logs for details
firebase functions:log --limit 50
```

### Firestore permissions errors
- Update security rules in Firestore Console
- See FIRESTORE_RULES.md

---

## Summary

✅ Cloud Functions initialized
✅ Agent spawning implemented
✅ Workflow execution implemented
✅ Job status tracking implemented
✅ Deep reasoning integrated
✅ Auto-cleanup scheduled
✅ Functions deployed to Firebase
