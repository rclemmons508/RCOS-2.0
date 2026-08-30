# Firestore Security Rules

This document contains security rules for production deployment.

## Development Rules (Test Mode)

For initial setup and testing, use permissive rules:

```javascript
rules_version = '3';

service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true; // Development only!
    }
  }
}
```

⚠️ **Never use in production!**

## Production Rules

Use these rules for customer-facing deployments:

```javascript
rules_version = '3';

service cloud.firestore {
  match /databases/{database}/documents {
    
    // ============================================
    // User Documents
    // ============================================
    match /users/{userId} {
      // Users can only read/write their own profile
      allow read, write: if request.auth.uid == userId;
      
      // All user subcollections inherit permissions
      match /{subcollection}/{document=**} {
        allow read, write: if request.auth.uid == userId;
      }
    }
    
    // ============================================
    // Company Profiles
    // ============================================
    match /users/{userId}/companyProfile/{document=**} {
      allow read: if request.auth.uid == userId;
      allow write: if request.auth.uid == userId &&
                      request.resource.data.size() <= 1000;
    }
    
    // ============================================
    // Workflows
    // ============================================
    match /users/{userId}/workflows/{workflowId} {
      allow read: if request.auth.uid == userId;
      allow create: if request.auth.uid == userId &&
                       request.resource.data.ownerId == userId &&
                       request.resource.data.status in ['active', 'inactive'];
      allow update: if request.auth.uid == userId &&
                       resource.data.ownerId == userId;
      allow delete: if request.auth.uid == userId;
    }
    
    // ============================================
    // Jobs
    // ============================================
    match /users/{userId}/jobs/{jobId} {
      allow read: if request.auth.uid == userId;
      allow create: if request.auth.uid == userId &&
                       request.resource.data.userId == userId;
      allow update: if request.auth.uid == userId &&
                       resource.data.userId == userId;
      allow delete: if request.auth.uid == userId &&
                       resource.data.status == 'completed';
    }
    
    // ============================================
    // Agents
    // ============================================
    match /users/{userId}/agents/{agentId} {
      allow read: if request.auth.uid == userId;
      allow create: if request.auth.uid == userId;
      allow update: if request.auth.uid == userId;
      allow delete: if request.auth.uid == userId &&
                       resource.data.status != 'running';
    }
    
    // ============================================
    // Agent Registry (Shared)
    // ============================================
    match /users/{userId}/agentRegistry/{agentId} {
      allow read: if request.auth.uid == userId;
      allow write: if request.auth.uid == userId &&
                      request.resource.data.status in ['active', 'inactive'];
    }
    
    // ============================================
    // Chat History
    // ============================================
    match /users/{userId}/chatHistory/{docId} {
      allow read: if request.auth.uid == userId;
      allow create: if request.auth.uid == userId &&
                       request.resource.data.userId == userId;
      allow delete: if request.auth.uid == userId &&
                       resource.data.userId == userId;
    }
    
    // ============================================
    // Audit Logs (Read-only after creation)
    // ============================================
    match /users/{userId}/auditLogs/{logId} {
      allow read: if request.auth.uid == userId;
      allow create: if request.auth.uid == userId;
      allow write, delete: if false; // Immutable
    }
    
    // ============================================
    // Reasoning History
    // ============================================
    match /users/{userId}/reasoning/{docId} {
      allow read: if request.auth.uid == userId;
      allow create: if request.auth.uid == userId;
      allow delete: if request.auth.uid == userId;
    }
  }
}
```

## Applying Rules to Firebase

### Via Firebase Console
1. Go to https://console.firebase.google.com
2. Select RCOS-2.0 project
3. Go to Firestore Database → Rules
4. Paste production rules
5. Click Publish

### Via Firebase CLI
```bash
# Save rules to firestore.rules file
cat > firestore.rules << 'EOF'
# Paste rules here
EOF

# Deploy rules
firebase deploy --only firestore:rules
```

## Testing Rules

Firebase provides a rules testing emulator:

```bash
# Start emulator
firebase emulators:start --only firestore

# In another terminal, run tests
firebase emulators:exec "npm test"
```

Example test file (`firestore.test.js`):

```javascript
const firebase = require('@firebase/rules-unit-testing');
const fs = require('fs');
const path = require('path');

const projectId = 'rcos-2-0';

beforeEach(async () => {
  await firebase.clearFirestoreData({ projectId });
});

after(async () => {
  await firebase.clearFirestoreData({ projectId });
});

const rules = fs.readFileSync(path.join(__dirname, 'firestore.rules'), 'utf8');
await firebase.loadFirestoreRules({ projectId, rules });

// Test: User can read own profile
test('User can read own profile', async () => {
  const db = firebase.initializeTestApp({ projectId, auth: { uid: 'user1' } });
  const doc = db.collection('users').doc('user1');
  await expect(doc.get()).not.toThrow();
});

// Test: User cannot read other's profile
test('User cannot read other profile', async () => {
  const db = firebase.initializeTestApp({ projectId, auth: { uid: 'user1' } });
  const doc = db.collection('users').doc('user2');
  await expect(doc.get()).toThrow();
});
```

## Security Best Practices

✅ **Do:**
- Use authenticated user IDs in all queries
- Validate data size limits
- Use timestamp validation
- Log all modifications via audit trail
- Regularly review access patterns

❌ **Don't:**
- Allow unauthenticated reads/writes
- Store sensitive data in Firestore (use Secrets Manager)
- Allow unlimited document size
- Skip field validation
- Leave test rules in production

## Monitoring Access

### View Firestore Usage
1. Go to Firebase Console → Firestore Database → Usage
2. Monitor:
   - Read operations
   - Write operations
   - Deleted documents

### Audit Logs
All user actions are logged in auditLogs collection for compliance tracking.

---

## Summary

✅ Development rules for testing
✅ Production rules for security
✅ Deployment instructions
✅ Testing guidelines
✅ Best practices documented
