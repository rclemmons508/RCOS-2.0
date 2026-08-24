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
class AgentRegistryTest {

    private lateinit var database: NovaDatabase
    private lateinit var agentDao: AgentRegistryDao
    private lateinit var auditLogDao: AuditLogDao

    private val workspaceAlpha = "ws_test_alpha"
    private val workspaceBeta = "ws_test_beta"

    private val adminUser = UserAccount(
        userId = "usr_admin",
        workspaceId = workspaceAlpha,
        email = "admin@alpha.com",
        fullName = "Alpha Admin",
        role = UserRole.ADMIN,
        accessLevel = AccessLevel.FULL_CONTROL
    )

    private val managerUser = UserAccount(
        userId = "usr_mgr",
        workspaceId = workspaceAlpha,
        email = "mgr@alpha.com",
        fullName = "Alpha Manager",
        role = UserRole.MANAGER,
        accessLevel = AccessLevel.WORKFLOW_ADMIN
    )

    private val employeeUser = UserAccount(
        userId = "usr_emp",
        workspaceId = workspaceAlpha,
        email = "emp@alpha.com",
        fullName = "Alpha Staff",
        role = UserRole.EMPLOYEE,
        accessLevel = AccessLevel.REGULAR_STAFF
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NovaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        agentDao = database.agentRegistryDao()
        auditLogDao = database.auditLogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAgentEntityCapabilityAndStatusHelpers() {
        val agent = AgentRegistryEntity(
            agentId = "ag_1",
            workspaceId = workspaceAlpha,
            agentName = "Ops Engine",
            agentDescription = "Automates operational tasks",
            agentType = AgentType.OPERATIONS_AGENT.name,
            status = AgentStatus.ACTIVE.name,
            capabilityProfile = "READ_DATA, EXECUTE_WORKFLOW, SEND_COMMUNICATION"
        )

        assertTrue(agent.canExecute())
        assertTrue(agent.hasCapability("READ_DATA"))
        assertTrue(agent.hasCapability("EXECUTE_WORKFLOW"))
        assertFalse(agent.hasCapability("ACCESS_FINANCIAL_DATA"))
        assertEquals(3, agent.getCapabilitiesList().size)

        val suspendedAgent = agent.copy(status = AgentStatus.SUSPENDED.name)
        assertFalse(suspendedAgent.canExecute())
    }

    @Test
    fun testAgentDaoWorkspaceIsolation() = runBlocking {
        val agentAlpha = AgentRegistryEntity(
            agentId = "ag_alpha_1",
            workspaceId = workspaceAlpha,
            agentName = "Alpha Operations Copilot",
            agentDescription = "Alpha team automation",
            status = AgentStatus.ACTIVE.name
        )

        val agentBeta = AgentRegistryEntity(
            agentId = "ag_beta_1",
            workspaceId = workspaceBeta,
            agentName = "Beta Risk Guardian",
            agentDescription = "Beta team automation",
            status = AgentStatus.ACTIVE.name
        )

        agentDao.insertAgent(agentAlpha)
        agentDao.insertAgent(agentBeta)

        val alphaAgents = agentDao.getWorkspaceAgents(workspaceAlpha).first()
        val betaAgents = agentDao.getWorkspaceAgents(workspaceBeta).first()

        assertEquals(1, alphaAgents.size)
        assertEquals("Alpha Operations Copilot", alphaAgents[0].agentName)

        assertEquals(1, betaAgents.size)
        assertEquals("Beta Risk Guardian", betaAgents[0].agentName)
    }

    @Test
    fun testPermissionEngineAgentManagementAuthorization() {
        // ADMIN can manage agents
        val adminResult = PermissionEngine.evaluatePermission(
            user = adminUser,
            targetWorkspaceId = workspaceAlpha,
            action = PermissionAction.MANAGE_AGENTS
        )
        assertTrue(adminResult.isAllowed)

        // MANAGER cannot create/update/disable agents (MANAGE_AGENTS is ADMIN only)
        val mgrResult = PermissionEngine.evaluatePermission(
            user = managerUser,
            targetWorkspaceId = workspaceAlpha,
            action = PermissionAction.MANAGE_AGENTS
        )
        assertFalse(mgrResult.isAllowed)
        assertTrue(mgrResult is AuthorizationResult.InsufficientRole)

        // EMPLOYEE cannot manage agents
        val empResult = PermissionEngine.evaluatePermission(
            user = employeeUser,
            targetWorkspaceId = workspaceAlpha,
            action = PermissionAction.MANAGE_AGENTS
        )
        assertFalse(empResult.isAllowed)

        // All team members can view agents
        val viewResult = PermissionEngine.evaluatePermission(
            user = employeeUser,
            targetWorkspaceId = workspaceAlpha,
            action = PermissionAction.VIEW_AGENTS
        )
        assertTrue(viewResult.isAllowed)
    }

    @Test
    fun testAgentLifecycleUpdatesAndAuditLogging() = runBlocking {
        val agentId = "ag_lifecycle_test"

        // 1. Register agent
        val newAgent = AgentRegistryEntity(
            agentId = agentId,
            workspaceId = workspaceAlpha,
            agentName = "Finance Auditor Bot",
            agentDescription = "Checks high-value financial logs",
            agentType = AgentType.FINANCE_AGENT.name,
            status = AgentStatus.ACTIVE.name,
            capabilityProfile = "READ_DATA,ACCESS_FINANCIAL_DATA",
            riskClassification = AgentRiskLevel.HIGH.name,
            createdBy = adminUser.email
        )
        agentDao.insertAgent(newAgent)

        auditLogDao.insertAuditEvent(
            AuditLogEntity(
                auditId = UUID.randomUUID().toString(),
                workspaceId = workspaceAlpha,
                userId = adminUser.userId,
                actorType = AuditActorType.USER.name,
                actorName = adminUser.fullName,
                actionType = "AGENT_CREATED",
                resourceType = "AI_AGENT",
                resourceId = agentId,
                agentId = agentId,
                timestamp = System.currentTimeMillis(),
                description = "Registered AI Agent '${newAgent.agentName}'",
                result = AuditResultStatus.SUCCESS.name,
                riskLevel = AuditRiskLevel.HIGH.name
            )
        )

        // 2. Disable agent
        val activeAgent = agentDao.getAgentByIdSync(agentId)
        assertNotNull(activeAgent)
        val disabledAgent = activeAgent!!.copy(status = AgentStatus.INACTIVE.name)
        agentDao.updateAgent(disabledAgent)

        auditLogDao.insertAuditEvent(
            AuditLogEntity(
                auditId = UUID.randomUUID().toString(),
                workspaceId = workspaceAlpha,
                userId = adminUser.userId,
                actorType = AuditActorType.USER.name,
                actorName = adminUser.fullName,
                actionType = "AGENT_DISABLED",
                resourceType = "AI_AGENT",
                resourceId = agentId,
                agentId = agentId,
                timestamp = System.currentTimeMillis(),
                description = "Disabled AI Agent '${disabledAgent.agentName}'",
                result = AuditResultStatus.SUCCESS.name,
                riskLevel = AuditRiskLevel.MEDIUM.name
            )
        )

        // Verify status in DB
        val updatedDbAgent = agentDao.getAgentByIdSync(agentId)
        assertNotNull(updatedDbAgent)
        assertEquals(AgentStatus.INACTIVE.name, updatedDbAgent!!.status)
        assertFalse(updatedDbAgent.canExecute())

        // Verify audit logs
        val auditEvents = auditLogDao.getWorkspaceAuditEvents(workspaceAlpha).first()
        assertEquals(2, auditEvents.size)
        assertTrue(auditEvents.any { it.actionType == "AGENT_CREATED" && it.agentId == agentId })
        assertTrue(auditEvents.any { it.actionType == "AGENT_DISABLED" && it.agentId == agentId })
    }
}
