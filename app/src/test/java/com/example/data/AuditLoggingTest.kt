package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuditLoggingTest {

    private lateinit var database: NovaDatabase
    private lateinit var auditLogDao: AuditLogDao

    private val workspaceA = "ws_corp_alpha"
    private val workspaceB = "ws_corp_beta"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NovaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        auditLogDao = database.auditLogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testRecordAuditEventAndWorkspaceIsolation() = runBlocking {
        val eventA1 = AuditLogEntity(
            auditId = UUID.randomUUID().toString(),
            workspaceId = workspaceA,
            userId = "usr_1",
            actorType = AuditActorType.USER.name,
            actorName = "Alice Admin",
            actionType = "USER_CREATED",
            timestamp = System.currentTimeMillis(),
            description = "Created user Bob",
            result = AuditResultStatus.SUCCESS.name,
            riskLevel = AuditRiskLevel.LOW.name
        )

        val eventA2 = AuditLogEntity(
            auditId = UUID.randomUUID().toString(),
            workspaceId = workspaceA,
            userId = "usr_2",
            actorType = AuditActorType.AI_AGENT.name,
            actorName = "Financial Copilot",
            actionType = "FINANCIAL_ACTION_REQUESTED",
            timestamp = System.currentTimeMillis() + 10,
            description = "Transferred $50,000",
            result = AuditResultStatus.SUCCESS.name,
            riskLevel = AuditRiskLevel.HIGH.name,
            approvalRequired = true,
            approvalStatus = AuditApprovalStatus.PENDING.name
        )

        val eventB1 = AuditLogEntity(
            auditId = UUID.randomUUID().toString(),
            workspaceId = workspaceB,
            userId = "usr_3",
            actorType = AuditActorType.USER.name,
            actorName = "Charlie Beta",
            actionType = "LOGIN",
            timestamp = System.currentTimeMillis() + 20,
            description = "Login successful",
            result = AuditResultStatus.SUCCESS.name,
            riskLevel = AuditRiskLevel.LOW.name
        )

        auditLogDao.insertAuditEvent(eventA1)
        auditLogDao.insertAuditEvent(eventA2)
        auditLogDao.insertAuditEvent(eventB1)

        val logsA = auditLogDao.getWorkspaceAuditEvents(workspaceA).first()
        val logsB = auditLogDao.getWorkspaceAuditEvents(workspaceB).first()

        assertEquals(2, logsA.size)
        assertEquals(1, logsB.size)

        assertTrue(logsA.all { it.workspaceId == workspaceA })
        assertTrue(logsB.all { it.workspaceId == workspaceB })

        assertEquals("usr_1", logsA.find { it.actionType == "USER_CREATED" }?.userId)
        assertEquals("Financial Copilot", logsA.find { it.actionType == "FINANCIAL_ACTION_REQUESTED" }?.actorName)
    }

    @Test
    fun testAuditLogFilteringByRiskAndResult() = runBlocking {
        val successEvent = AuditLogEntity(
            auditId = UUID.randomUUID().toString(),
            workspaceId = workspaceA,
            userId = "usr_1",
            actorType = AuditActorType.USER.name,
            actorName = "Alice",
            actionType = "WORKSPACE_SWITCHED",
            timestamp = System.currentTimeMillis(),
            description = "Switched active workspace",
            result = AuditResultStatus.SUCCESS.name,
            riskLevel = AuditRiskLevel.LOW.name
        )

        val deniedEvent = AuditLogEntity(
            auditId = UUID.randomUUID().toString(),
            workspaceId = workspaceA,
            userId = "usr_emp",
            actorType = AuditActorType.USER.name,
            actorName = "David Employee",
            actionType = "AUTHORIZATION_DENIED",
            timestamp = System.currentTimeMillis() + 5,
            description = "Access denied modifying governance",
            result = AuditResultStatus.DENIED.name,
            riskLevel = AuditRiskLevel.HIGH.name
        )

        auditLogDao.insertAuditEvent(successEvent)
        auditLogDao.insertAuditEvent(deniedEvent)

        val allLogs = auditLogDao.getWorkspaceAuditEvents(workspaceA).first()
        assertEquals(2, allLogs.size)

        val highRiskLogs = allLogs.filter { it.riskLevel == AuditRiskLevel.HIGH.name }
        assertEquals(1, highRiskLogs.size)
        assertEquals(AuditResultStatus.DENIED.name, highRiskLogs[0].result)

        val deniedLogs = allLogs.filter { it.result == AuditResultStatus.DENIED.name }
        assertEquals(1, deniedLogs.size)
        assertEquals("AUTHORIZATION_DENIED", deniedLogs[0].actionType)
    }

    @Test
    fun testRbacPermissionForAuditLogView() {
        val admin = UserAccount(
            userId = "u_admin",
            workspaceId = workspaceA,
            email = "admin@a.com",
            fullName = "Admin User",
            role = UserRole.ADMIN,
            accessLevel = AccessLevel.FULL_CONTROL,
            department = "Exec",
            avatarColorHex = "#000000",
            registeredAt = 0,
            lastActiveAt = 0
        )

        val auditor = UserAccount(
            userId = "u_auditor",
            workspaceId = workspaceA,
            email = "auditor@a.com",
            fullName = "Auditor User",
            role = UserRole.AUDITOR,
            accessLevel = AccessLevel.READ_ONLY,
            department = "Compliance",
            avatarColorHex = "#000000",
            registeredAt = 0,
            lastActiveAt = 0
        )

        val manager = UserAccount(
            userId = "u_mgr",
            workspaceId = workspaceA,
            email = "mgr@a.com",
            fullName = "Manager User",
            role = UserRole.MANAGER,
            accessLevel = AccessLevel.WORKFLOW_ADMIN,
            department = "Ops",
            avatarColorHex = "#000000",
            registeredAt = 0,
            lastActiveAt = 0
        )

        val employee = UserAccount(
            userId = "u_emp",
            workspaceId = workspaceA,
            email = "emp@a.com",
            fullName = "Employee User",
            role = UserRole.EMPLOYEE,
            accessLevel = AccessLevel.READ_ONLY,
            department = "Staff",
            avatarColorHex = "#000000",
            registeredAt = 0,
            lastActiveAt = 0
        )

        // ADMIN -> Allowed
        assertTrue(PermissionEngine.evaluatePermission(admin, workspaceA, PermissionAction.VIEW_AUDIT_LOGS).isAllowed)

        // AUDITOR -> Allowed
        assertTrue(PermissionEngine.evaluatePermission(auditor, workspaceA, PermissionAction.VIEW_AUDIT_LOGS).isAllowed)

        // MANAGER -> Allowed
        assertTrue(PermissionEngine.evaluatePermission(manager, workspaceA, PermissionAction.VIEW_AUDIT_LOGS).isAllowed)

        // EMPLOYEE -> Denied
        val empResult = PermissionEngine.evaluatePermission(employee, workspaceA, PermissionAction.VIEW_AUDIT_LOGS)
        assertFalse(empResult.isAllowed)
        assertTrue(empResult is AuthorizationResult.InsufficientRole)

        // Cross-workspace access -> Denied
        val crossWorkspaceResult = PermissionEngine.evaluatePermission(admin, workspaceB, PermissionAction.VIEW_AUDIT_LOGS)
        assertFalse(crossWorkspaceResult.isAllowed)
        assertTrue(crossWorkspaceResult is AuthorizationResult.NotAMember)
    }
}
