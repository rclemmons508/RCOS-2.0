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
class WorkspaceOnboardingTest {

    private lateinit var database: NovaDatabase
    private lateinit var onboardingDao: WorkspaceOnboardingDao
    private lateinit var agentDao: AgentRegistryDao
    private lateinit var auditDao: AuditLogDao
    private lateinit var workspaceDao: WorkspaceDao
    private lateinit var appDao: AppDao
    private lateinit var workflowDao: WorkflowDao
    private lateinit var repository: NovaRepository

    private val workspaceId = "ws_test_onboarding"
    private val adminUser = UserAccount(
        userId = "usr_admin_onb",
        workspaceId = workspaceId,
        email = "admin@onboarding.com",
        fullName = "Onboarding Admin",
        role = UserRole.ADMIN,
        accessLevel = AccessLevel.FULL_CONTROL
    )
    private val employeeUser = UserAccount(
        userId = "usr_staff_onb",
        workspaceId = workspaceId,
        email = "staff@onboarding.com",
        fullName = "Staff User",
        role = UserRole.EMPLOYEE,
        accessLevel = AccessLevel.REGULAR_STAFF
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NovaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        onboardingDao = database.workspaceOnboardingDao()
        agentDao = database.agentRegistryDao()
        auditDao = database.auditLogDao()
        workspaceDao = database.workspaceDao()
        appDao = database.appDao()
        workflowDao = database.workflowDao()

        repository = NovaRepository(
            dao = appDao,
            workflowDao = workflowDao,
            workspaceDao = workspaceDao,
            auditLogDao = auditDao,
            agentRegistryDao = agentDao,
            workspaceOnboardingDao = onboardingDao
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testIndustryTemplatesAvailability() {
        IndustryType.values().forEach { type ->
            val template = IndustryTemplate.getTemplate(type)
            assertNotNull(template)
            assertTrue(template.displayName.isNotBlank())
            assertTrue(template.recommendedAgents.isNotEmpty())
            assertTrue(template.defaultWorkflows.isNotEmpty())
            assertTrue(template.defaultAutoApprovalRiskThreshold > 0)
        }
    }

    @Test
    fun testOnboardingDaoCrudAndQuery() = runBlocking {
        val onboardingId = "onb_test_1"
        val entity = WorkspaceOnboardingEntity(
            onboardingId = onboardingId,
            workspaceId = workspaceId,
            companyName = "Apex Healthcare",
            industry = IndustryType.HEALTHCARE.name,
            companySize = "100-500",
            domain = "apexhealth.org",
            primaryBottleneck = "Patient record verification backlog",
            targetAutomationGoal = "Automate 80% of patient triage workflows",
            targetReductionPercent = 80,
            selectedAgentPackage = "Healthcare Compliance Suite",
            governanceProfile = "Strict HIPAA Governance",
            createdBy = adminUser.fullName,
            onboardingStatus = OnboardingStatus.STARTED.name
        )

        onboardingDao.insertOnboarding(entity)

        val retrievedFlow = onboardingDao.getOnboardingByWorkspace(workspaceId).first()
        assertNotNull(retrievedFlow)
        assertEquals("Apex Healthcare", retrievedFlow?.companyName)
        assertEquals(OnboardingStatus.STARTED.name, retrievedFlow?.onboardingStatus)

        // Update progress
        val updated = retrievedFlow!!.copy(
            onboardingStatus = OnboardingStatus.AGENTS_PROVISIONED.name
        )
        onboardingDao.updateOnboarding(updated)

        val retrievedSync = onboardingDao.getOnboardingByWorkspaceSync(workspaceId)
        assertNotNull(retrievedSync)
        assertEquals(OnboardingStatus.AGENTS_PROVISIONED.name, retrievedSync?.onboardingStatus)
    }

    @Test
    fun testPermissionEngineOnboardingAuthorization() {
        // ADMIN can manage onboarding
        val adminResult = PermissionEngine.evaluatePermission(
            user = adminUser,
            targetWorkspaceId = workspaceId,
            action = PermissionAction.MANAGE_ONBOARDING
        )
        assertTrue(adminResult.isAllowed)

        // EMPLOYEE cannot manage onboarding
        val staffResult = PermissionEngine.evaluatePermission(
            user = employeeUser,
            targetWorkspaceId = workspaceId,
            action = PermissionAction.MANAGE_ONBOARDING
        )
        assertFalse(staffResult.isAllowed)
        assertTrue(staffResult is AuthorizationResult.InsufficientRole)

        // All users can view onboarding status
        val viewResult = PermissionEngine.evaluatePermission(
            user = employeeUser,
            targetWorkspaceId = workspaceId,
            action = PermissionAction.VIEW_ONBOARDING
        )
        assertTrue(viewResult.isAllowed)
    }

    @Test
    fun testAgentProvisioningServiceIntegration() = runBlocking {
        val provisionedAgents = AgentProvisioningService.provisionAgentsForWorkspace(
            workspaceId = workspaceId,
            industryType = IndustryType.FINANCE,
            selectedPackageName = "Financial Services Suite",
            createdByActor = adminUser.fullName,
            repository = repository
        )

        assertTrue(provisionedAgents.isNotEmpty())
        val workspaceAgentsInDb = agentDao.getWorkspaceAgents(workspaceId).first()
        assertEquals(provisionedAgents.size, workspaceAgentsInDb.size)

        // Verify audit logs were generated for provisioned agents
        val auditLogs = auditDao.getWorkspaceAuditEvents(workspaceId).first()
        assertTrue(auditLogs.isNotEmpty())
        assertTrue(auditLogs.any { it.actionType == "AGENTS_PROVISIONED" })
    }
}
