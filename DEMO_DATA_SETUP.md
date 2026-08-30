# Demo Data Setup

This guide loads realistic sample data into Firestore for customer demos.

## Overview

We'll create:
- Demo user account
- Sample company profile
- Pre-built workflows
- Agent registry
- Sample jobs with results

## Step 1: Create Demo User in Firebase

### Via Firebase Console
1. Go to https://console.firebase.google.com
2. Select RCOS-2.0 project
3. Go to Authentication → Users
4. Click "Add user"
5. Create demo account:
   - Email: `demo@rcos.demo`
   - Password: `Demo@12345`
6. Note the User ID (copy it)

### Via Firebase CLI
```bash
# Install firebase-tools if not already done
npm install -g firebase-tools
firebase auth:import users.json --hash-algo=scrypt

# Create users.json with:
```

```json
[
  {
    "localId": "demo-user-001",
    "email": "demo@rcos.demo",
    "emailVerified": true,
    "passwordHash": "fakeHashValue",
    "salt": "fakeSaltValue",
    "createdAt": 1630000000000,
    "lastLoginAt": 1630000000000,
    "customAttributes": "{}"
  }
]
```

## Step 2: Create Firestore Sample Data

Create `firestore_seed.js`:

```javascript
const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

// IMPORTANT: Get the actual user ID from Firebase Console
const DEMO_USER_ID = 'demo-user-001'; // Replace with actual ID

async function seedData() {
  console.log('🌱 Seeding Firestore with demo data...');

  try {
    // 1. Create User Profile
    console.log('📝 Creating user profile...');
    await db.collection('users').doc(DEMO_USER_ID).set({
      email: 'demo@rcos.demo',
      displayName: 'Demo User',
      companyName: 'Acme Corp Demo',
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      lastLogin: admin.firestore.FieldValue.serverTimestamp(),
      plan: 'demo',
      status: 'active'
    });

    // 2. Create Company Profile
    console.log('🏢 Creating company profile...');
    await db.collection('users').doc(DEMO_USER_ID)
      .collection('companyProfile').doc('main').set({
        name: 'Acme Corp Demo',
        industry: 'Technology',
        size: '50-100 employees',
        bottleneck: 'Customer support ticket volume',
        targetReduction: 60,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });

    // 3. Create Agent Registry
    console.log('🤖 Creating agent registry...');
    const agents = [
      {
        id: 'email-handler',
        name: 'Email Handler',
        description: 'Processes and routes incoming emails',
        type: 'communication',
        capabilities: ['email_parsing', 'routing', 'categorization'],
        status: 'active'
      },
      {
        id: 'ticket-resolver',
        name: 'Ticket Resolver',
        description: 'Resolves support tickets automatically',
        type: 'support',
        capabilities: ['ticket_analysis', 'solution_generation', 'escalation'],
        status: 'active'
      },
      {
        id: 'knowledge-bot',
        name: 'Knowledge Bot',
        description: 'Searches knowledge base for answers',
        type: 'knowledge',
        capabilities: ['search', 'context_retrieval', 'answer_generation'],
        status: 'active'
      },
      {
        id: 'lead-qualifier',
        name: 'Lead Qualifier',
        description: 'Qualifies and scores sales leads',
        type: 'sales',
        capabilities: ['lead_scoring', 'qualification', 'assignment'],
        status: 'active'
      }
    ];

    for (const agent of agents) {
      await db.collection('users').doc(DEMO_USER_ID)
        .collection('agentRegistry').doc(agent.id).set(agent);
    }

    // 4. Create Workflows
    console.log('⚙️ Creating workflows...');
    const workflows = [
      {
        id: 'workflow-support',
        name: 'Customer Support Automation',
        description: 'Automates customer support ticket handling',
        industry: 'Technology',
        agents: ['email-handler', 'ticket-resolver', 'knowledge-bot'],
        expectedReduction: 60,
        status: 'active',
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      },
      {
        id: 'workflow-sales',
        name: 'Lead Qualification Pipeline',
        description: 'Qualifies and scores incoming sales leads',
        industry: 'Technology',
        agents: ['lead-qualifier'],
        expectedReduction: 45,
        status: 'active',
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      }
    ];

    for (const workflow of workflows) {
      await db.collection('users').doc(DEMO_USER_ID)
        .collection('workflows').doc(workflow.id).set(workflow);
    }

    // 5. Create Sample Jobs with Results
    console.log('✅ Creating sample jobs...');
    const jobs = [
      {
        id: 'job-001',
        workflowId: 'workflow-support',
        status: 'completed',
        createdAt: new Date(Date.now() - 24 * 60 * 60 * 1000),
        completedAt: new Date(Date.now() - 23 * 60 * 60 * 1000),
        results: [
          {
            agentType: 'email-handler',
            ticketsProcessed: 47,
            categorized: 47,
            timeSpent: '12 minutes'
          },
          {
            agentType: 'ticket-resolver',
            resolved: 31,
            escalated: 16,
            avgSolveTime: '4 minutes'
          },
          {
            agentType: 'knowledge-bot',
            queriesRun: 28,
            solutionsFound: 28,
            accuracy: '94%'
          }
        ],
        stats: {
          ticketsProcessed: 47,
          ticketsResolved: 31,
          resolutionRate: 66,
          timeReducedPercent: 58,
          costSavings: '$234'
        }
      },
      {
        id: 'job-002',
        workflowId: 'workflow-support',
        status: 'completed',
        createdAt: new Date(Date.now() - 48 * 60 * 60 * 1000),
        completedAt: new Date(Date.now() - 47 * 60 * 60 * 1000),
        results: [
          {
            agentType: 'email-handler',
            ticketsProcessed: 52,
            categorized: 52
          },
          {
            agentType: 'ticket-resolver',
            resolved: 35,
            escalated: 17
          }
        ],
        stats: {
          ticketsProcessed: 52,
          ticketsResolved: 35,
          resolutionRate: 67,
          timeReducedPercent: 61
        }
      },
      {
        id: 'job-003',
        workflowId: 'workflow-sales',
        status: 'completed',
        createdAt: new Date(Date.now() - 12 * 60 * 60 * 1000),
        completedAt: new Date(Date.now() - 11 * 60 * 60 * 1000),
        results: [
          {
            agentType: 'lead-qualifier',
            leadsProcessed: 156,
            qualified: 68,
            qualificationRate: 44
          }
        ],
        stats: {
          leadsProcessed: 156,
          qualified: 68,
          timeReducedPercent: 45,
          costPerLead: '$12'
        }
      }
    ];

    for (const job of jobs) {
      const id = job.id;
      delete job.id;
      await db.collection('users').doc(DEMO_USER_ID)
        .collection('jobs').doc(id).set(job);
    }

    // 6. Create Chat History
    console.log('💬 Creating chat history...');
    const chats = [
      {
        role: 'user',
        content: 'How can I reduce support ticket volume?',
        timestamp: admin.firestore.FieldValue.serverTimestamp()
      },
      {
        role: 'assistant',
        content: `Based on your company profile, I recommend:

1. **Email Handler Agent**: Automatically categorize incoming support emails into categories (bug, feature request, billing, etc.)

2. **Knowledge Bot**: Deploy a bot that searches your knowledge base and FAQs to provide instant answers to 60% of common questions

3. **Ticket Resolver**: Use AI to suggest solutions and auto-resolve tickets where confidence is high

Expected results: 58-61% reduction in manual support hours, 66% auto-resolution rate`,
        timestamp: admin.firestore.FieldValue.serverTimestamp()
      }
    ];

    for (const chat of chats) {
      await db.collection('users').doc(DEMO_USER_ID)
        .collection('chatHistory').add(chat);
    }

    // 7. Create Audit Log
    console.log('📋 Creating audit log...');
    const auditLogs = [
      {
        action: 'LOGIN',
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        details: { method: 'email' }
      },
      {
        action: 'PROFILE_CREATED',
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        details: { industry: 'Technology' }
      },
      {
        action: 'WORKFLOW_CREATED',
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        details: { workflowId: 'workflow-support' }
      },
      {
        action: 'JOB_EXECUTED',
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        details: { jobId: 'job-001', workflowId: 'workflow-support' }
      }
    ];

    for (const log of auditLogs) {
      await db.collection('users').doc(DEMO_USER_ID)
        .collection('auditLogs').add(log);
    }

    console.log('\n✨ Demo data seeded successfully!');
    console.log(`📧 Demo credentials:`);
    console.log(`   Email: demo@rcos.demo`);
    console.log(`   Password: Demo@12345`);
    console.log(`\n🎯 Try these actions in the app:`);
    console.log(`   1. Login with demo account`);
    console.log(`   2. View Dashboard - see job execution results`);
    console.log(`   3. Go to Workflows - see pre-built automation workflows`);
    console.log(`   4. Chat screen - ask "How can I automate my business?"`);
    console.log(`   5. View Jobs history - see execution details and stats`);

    process.exit(0);
  } catch (error) {
    console.error('❌ Error seeding data:', error);
    process.exit(1);
  }
}

seedData();
```

## Step 3: Download Service Account Key

1. Go to Firebase Console → Project Settings (gear icon)
2. Click "Service Accounts" tab
3. Click "Generate new private key"
4. Save as `service-account-key.json` in RCOS-2.0 directory
5. **Add to .gitignore** (never commit this!)

```bash
echo "service-account-key.json" >> .gitignore
```

## Step 4: Run Data Seeding Script

```bash
# From RCOS-2.0 directory
node firestore_seed.js

# Output:
# 🌱 Seeding Firestore with demo data...
# 📝 Creating user profile...
# 🏢 Creating company profile...
# 🤖 Creating agent registry...
# ⚙️ Creating workflows...
# ✅ Creating sample jobs...
# 💬 Creating chat history...
# 📋 Creating audit log...
#
# ✨ Demo data seeded successfully!
# 📧 Demo credentials:
#    Email: demo@rcos.demo
#    Password: Demo@12345
```

## Step 5: Test with App

1. Build and run the app
2. Login with:
   - Email: `demo@rcos.demo`
   - Password: `Demo@12345`
3. You should see:
   - Dashboard with company profile
   - Workflow cards showing automation opportunities
   - Job history with execution results
   - Agent registry

## Viewing Data in Firebase Console

1. Go to https://console.firebase.google.com
2. Select RCOS-2.0 project
3. Go to Firestore Database
4. Browse collections:
   - users/demo-user-001/companyProfile
   - users/demo-user-001/workflows
   - users/demo-user-001/jobs
   - users/demo-user-001/agentRegistry
   - users/demo-user-001/chatHistory

## Creating Additional Demo Users

Edit `firestore_seed.js` and create another user:

```javascript
const DEMO_USER_ID = 'demo-user-002'; // Different user

// Update company profile:
await db.collection('users').doc(DEMO_USER_ID)
  .collection('companyProfile').doc('main').set({
    name: 'Tech Startup Demo',
    industry: 'Finance',  // Different industry
    size: '20-50 employees',
    bottleneck: 'Lead generation and qualification',
    targetReduction: 50
  });
```

## Backup and Restore

### Export Firestore Data
```bash
firebase firestore:export ./backups/demo-data-backup
```

### Restore from Backup
```bash
firebase firestore:import ./backups/demo-data-backup
```

## Cleanup (Reset Demo)

If you need to clear all demo data:

```bash
firebase firestore:delete --recursive --yes
```

Then re-run the seeding script.

---

## Summary

✅ Demo user created
✅ Company profile seeded
✅ Workflows created
✅ Agent registry populated
✅ Sample jobs with results
✅ Chat history added
✅ Audit trail created
✅ Ready for customer demo!
