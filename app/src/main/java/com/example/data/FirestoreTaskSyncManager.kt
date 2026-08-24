package com.example.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class FirestoreSyncState {
    CONNECTED,
    SYNCING,
    OFFLINE_PERSISTENCE,
    INITIALIZING,
    ERROR
}

data class FirestoreSyncInfo(
    val state: FirestoreSyncState = FirestoreSyncState.INITIALIZING,
    val statusMessage: String = "Connecting to Firestore...",
    val lastSyncedTime: Long = System.currentTimeMillis(),
    val pendingSyncCount: Int = 0,
    val activeDevicesCount: Int = 1,
    val collectionName: String = "rcos_agent_tasks"
)

class FirestoreTaskSyncManager(
    private val scope: CoroutineScope
) {
    private val tag = "FirestoreTaskSync"

    private val _syncInfo = MutableStateFlow(FirestoreSyncInfo())
    val syncInfo: StateFlow<FirestoreSyncInfo> = _syncInfo.asStateFlow()

    private var firestore: FirebaseFirestore? = null
    private var tasksListenerRegistration: ListenerRegistration? = null
    private var approvalsListenerRegistration: ListenerRegistration? = null

    init {
        initializeFirestore()
    }

    private fun initializeFirestore() {
        try {
            val db = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .build()
            db.firestoreSettings = settings
            firestore = db
            _syncInfo.value = FirestoreSyncInfo(
                state = FirestoreSyncState.CONNECTED,
                statusMessage = "Real-time Multi-Device Sync Active",
                lastSyncedTime = System.currentTimeMillis()
            )
            Log.d(tag, "Firebase Firestore initialized successfully with offline persistence.")
        } catch (t: Throwable) {
            Log.w(tag, "Firebase Firestore init exception (using offline cache fallback): ${t.message}")
            _syncInfo.value = FirestoreSyncInfo(
                state = FirestoreSyncState.OFFLINE_PERSISTENCE,
                statusMessage = "Offline Local Persistence Active"
            )
        }
    }

    /**
     * Start real-time snapshot listener on Firestore tasks collection.
     * When any device modifies a task status (Pending -> Completed -> Archived),
     * this listener immediately fires with the updated task list.
     */
    fun startRealtimeTasksListener(
        onTasksUpdated: (List<JobEntity>) -> Unit
    ) {
        val db = firestore ?: return
        try {
            tasksListenerRegistration?.remove()
            tasksListenerRegistration = db.collection("rcos_agent_tasks")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w(tag, "Firestore tasks snapshot error: ${error.message}")
                        _syncInfo.value = _syncInfo.value.copy(
                            state = FirestoreSyncState.OFFLINE_PERSISTENCE,
                            statusMessage = "Offline Cache (Cloud stream paused)"
                        )
                        return@addSnapshotListener
                    }

                    if (snapshots != null && !snapshots.isEmpty) {
                        val updatedJobs = snapshots.documents.mapNotNull { doc ->
                            documentToJobEntity(doc)
                        }
                        _syncInfo.value = _syncInfo.value.copy(
                            state = FirestoreSyncState.CONNECTED,
                            statusMessage = "Real-time Cloud Synced (${updatedJobs.size} tasks)",
                            lastSyncedTime = System.currentTimeMillis()
                        )
                        onTasksUpdated(updatedJobs)
                    }
                }
        } catch (t: Throwable) {
            Log.e(tag, "Failed to attach real-time tasks listener", t)
        }
    }

    /**
     * Start real-time snapshot listener on Firestore approvals collection.
     */
    fun startRealtimeApprovalsListener(
        onApprovalsUpdated: (List<ApprovalItem>) -> Unit
    ) {
        val db = firestore ?: return
        try {
            approvalsListenerRegistration?.remove()
            approvalsListenerRegistration = db.collection("rcos_agent_approvals")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w(tag, "Firestore approvals snapshot error: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshots != null && !snapshots.isEmpty) {
                        val updatedApprovals = snapshots.documents.mapNotNull { doc ->
                            documentToApprovalItem(doc)
                        }
                        onApprovalsUpdated(updatedApprovals)
                    }
                }
        } catch (t: Throwable) {
            Log.e(tag, "Failed to attach real-time approvals listener", t)
        }
    }

    /**
     * Push a task update (or creation) to Firestore in real time.
     */
    fun pushTaskUpdate(job: JobEntity) {
        val db = firestore ?: return
        scope.launch(Dispatchers.IO) {
            try {
                _syncInfo.value = _syncInfo.value.copy(
                    state = FirestoreSyncState.SYNCING,
                    statusMessage = "Broadcasting ${job.id} update..."
                )
                val docData = hashMapOf(
                    "id" to job.id,
                    "title" to job.title,
                    "clientName" to job.clientName,
                    "status" to job.status,
                    "assignedAgent" to job.assignedAgent,
                    "priority" to job.priority,
                    "dueDate" to job.dueDate,
                    "approvalRequired" to job.approvalRequired,
                    "isApproved" to job.isApproved,
                    "progress" to job.progress.toDouble(),
                    "summary" to job.summary,
                    "lastUpdatedTimestamp" to System.currentTimeMillis()
                )
                db.collection("rcos_agent_tasks")
                    .document(job.id.replace("#", "").replace("/", "_"))
                    .set(docData, SetOptions.merge())
                    .await()

                _syncInfo.value = _syncInfo.value.copy(
                    state = FirestoreSyncState.CONNECTED,
                    statusMessage = "Real-time Multi-Device Sync Active",
                    lastSyncedTime = System.currentTimeMillis()
                )
                Log.d(tag, "Synced task ${job.id} to Firestore successfully.")
            } catch (t: Throwable) {
                Log.w(tag, "Failed to push task update to Firestore: ${t.message}")
                _syncInfo.value = _syncInfo.value.copy(
                    state = FirestoreSyncState.OFFLINE_PERSISTENCE,
                    statusMessage = "Saved Locally (Will sync when online)"
                )
            }
        }
    }

    /**
     * Push an approval update to Firestore in real time.
     */
    fun pushApprovalUpdate(item: ApprovalItem) {
        val db = firestore ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val docData = hashMapOf(
                    "id" to item.id,
                    "workflowTitle" to item.workflowTitle,
                    "requestedByAgent" to item.requestedByAgent,
                    "triggerSource" to item.triggerSource,
                    "timestamp" to item.timestamp,
                    "riskScorePct" to item.riskScorePct,
                    "summary" to item.summary,
                    "proposedAction" to item.proposedAction,
                    "status" to item.status.name,
                    "reviewedBy" to (item.reviewedBy ?: ""),
                    "reviewNotes" to (item.reviewNotes ?: ""),
                    "lastUpdatedTimestamp" to System.currentTimeMillis()
                )
                db.collection("rcos_agent_approvals")
                    .document(item.id.replace("#", "").replace("/", "_"))
                    .set(docData, SetOptions.merge())
                    .await()
                Log.d(tag, "Synced approval ${item.id} to Firestore successfully.")
            } catch (t: Throwable) {
                Log.w(tag, "Failed to push approval update to Firestore: ${t.message}")
            }
        }
    }

    /**
     * Initial seed/sync of local tasks to Firestore if cloud collection is newly provisioned.
     */
    fun seedTasksToFirestore(jobs: List<JobEntity>) {
        val db = firestore ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val batch = db.batch()
                jobs.forEach { job ->
                    val docRef = db.collection("rcos_agent_tasks")
                        .document(job.id.replace("#", "").replace("/", "_"))
                    val docData = hashMapOf(
                        "id" to job.id,
                        "title" to job.title,
                        "clientName" to job.clientName,
                        "status" to job.status,
                        "assignedAgent" to job.assignedAgent,
                        "priority" to job.priority,
                        "dueDate" to job.dueDate,
                        "approvalRequired" to job.approvalRequired,
                        "isApproved" to job.isApproved,
                        "progress" to job.progress.toDouble(),
                        "summary" to job.summary,
                        "lastUpdatedTimestamp" to System.currentTimeMillis()
                    )
                    batch.set(docRef, docData, SetOptions.merge())
                }
                batch.commit().await()
                Log.d(tag, "Seeded ${jobs.size} initial tasks to Firestore.")
            } catch (t: Throwable) {
                Log.w(tag, "Error during initial Firestore task batch sync: ${t.message}")
            }
        }
    }

    /**
     * Initial seed/sync of approvals to Firestore.
     */
    fun seedApprovalsToFirestore(items: List<ApprovalItem>) {
        val db = firestore ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val batch = db.batch()
                items.forEach { item ->
                    val docRef = db.collection("rcos_agent_approvals")
                        .document(item.id.replace("#", "").replace("/", "_"))
                    val docData = hashMapOf(
                        "id" to item.id,
                        "workflowTitle" to item.workflowTitle,
                        "requestedByAgent" to item.requestedByAgent,
                        "triggerSource" to item.triggerSource,
                        "timestamp" to item.timestamp,
                        "riskScorePct" to item.riskScorePct,
                        "summary" to item.summary,
                        "proposedAction" to item.proposedAction,
                        "status" to item.status.name,
                        "reviewedBy" to (item.reviewedBy ?: ""),
                        "reviewNotes" to (item.reviewNotes ?: ""),
                        "lastUpdatedTimestamp" to System.currentTimeMillis()
                    )
                    batch.set(docRef, docData, SetOptions.merge())
                }
                batch.commit().await()
                Log.d(tag, "Seeded ${items.size} approvals to Firestore.")
            } catch (t: Throwable) {
                Log.w(tag, "Error during initial Firestore approvals batch sync: ${t.message}")
            }
        }
    }

    fun cleanup() {
        tasksListenerRegistration?.remove()
        approvalsListenerRegistration?.remove()
    }

    private fun documentToJobEntity(doc: DocumentSnapshot): JobEntity? {
        return try {
            val id = doc.getString("id") ?: doc.id
            val title = doc.getString("title") ?: return null
            val clientName = doc.getString("clientName") ?: "Client"
            val status = doc.getString("status") ?: "Pending"
            val assignedAgent = doc.getString("assignedAgent") ?: "Agent"
            val priority = doc.getString("priority") ?: "Normal"
            val dueDate = doc.getString("dueDate") ?: "Today"
            val approvalRequired = doc.getBoolean("approvalRequired") ?: false
            val isApproved = doc.getBoolean("isApproved") ?: (status == "Completed")
            val progress = doc.getDouble("progress")?.toFloat() ?: (if (status == "Completed") 1.0f else 0.5f)
            val summary = doc.getString("summary") ?: ""

            JobEntity(
                id = if (id.startsWith("#")) id else "#$id",
                title = title,
                clientName = clientName,
                status = status,
                assignedAgent = assignedAgent,
                priority = priority,
                dueDate = dueDate,
                approvalRequired = approvalRequired,
                isApproved = isApproved,
                progress = progress,
                summary = summary
            )
        } catch (e: Exception) {
            Log.w(tag, "Error parsing Firestore task document: ${e.message}")
            null
        }
    }

    private fun documentToApprovalItem(doc: DocumentSnapshot): ApprovalItem? {
        return try {
            val id = doc.getString("id") ?: doc.id
            val workflowTitle = doc.getString("workflowTitle") ?: return null
            val triggerSource = doc.getString("triggerSource") ?: "Trigger"
            val summary = doc.getString("summary") ?: ""
            val proposedAction = doc.getString("proposedAction") ?: ""
            val riskScorePct = doc.getLong("riskScorePct")?.toInt() ?: 20
            val statusStr = doc.getString("status") ?: "PENDING"
            val status = try {
                ApprovalStatus.valueOf(statusStr)
            } catch (e: Exception) {
                ApprovalStatus.PENDING
            }
            val timestamp = doc.getString("timestamp") ?: "Just now"
            val reviewedBy = doc.getString("reviewedBy")
            val reviewNotes = doc.getString("reviewNotes")
            val requestedByAgent = doc.getString("requestedByAgent") ?: "AI Agent"

            ApprovalItem(
                id = id,
                workflowTitle = workflowTitle,
                requestedByAgent = requestedByAgent,
                triggerSource = triggerSource,
                timestamp = timestamp,
                riskScorePct = riskScorePct,
                summary = summary,
                proposedAction = proposedAction,
                status = status,
                reviewedBy = reviewedBy,
                reviewNotes = reviewNotes
            )
        } catch (e: Exception) {
            Log.w(tag, "Error parsing Firestore approval document: ${e.message}")
            null
        }
    }
}
