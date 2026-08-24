package com.example.data

import org.junit.Assert.*
import org.junit.Test

class PermissionEngineTest {

    private val workspaceA = "ws_tech_corp"
    private val workspaceB = "ws_finance_inc"

    private val adminUserA = UserAccount(
        userId = "usr_admin",
        workspaceId = workspaceA,
        email = "admin@techcorp.com",
        fullName = "Alice Admin",
        role = UserRole.ADMIN,
        accessLevel = AccessLevel.FULL_CONTROL,
        department = "Executive",
        avatarColorHex = "#3B82F6",
        registeredAt = System.currentTimeMillis(),
        lastActiveAt = System.currentTimeMillis()
    )

    private val managerUserA = UserAccount(
        userId = "usr_manager",
        workspaceId = workspaceA,
        email = "manager@techcorp.com",
        fullName = "Bob Manager",
        role = UserRole.MANAGER,
        accessLevel = AccessLevel.WORKFLOW_ADMIN,
        department = "Operations",
        avatarColorHex = "#10B981",
        registeredAt = System.currentTimeMillis(),
        lastActiveAt = System.currentTimeMillis()
    )

    private val employeeUserA = UserAccount(
        userId = "usr_employee",
        workspaceId = workspaceA,
        email = "employee@techcorp.com",
        fullName = "Charlie Employee",
        role = UserRole.EMPLOYEE,
        accessLevel = AccessLevel.REGULAR_STAFF,
        department = "Support",
        avatarColorHex = "#8B5CF6",
        registeredAt = System.currentTimeMillis(),
        lastActiveAt = System.currentTimeMillis()
    )

    private val auditorUserA = UserAccount(
        userId = "usr_auditor",
        workspaceId = workspaceA,
        email = "auditor@techcorp.com",
        fullName = "Diana Auditor",
        role = UserRole.AUDITOR,
        accessLevel = AccessLevel.READ_ONLY,
        department = "Compliance",
        avatarColorHex = "#EC4899",
        registeredAt = System.currentTimeMillis(),
        lastActiveAt = System.currentTimeMillis()
    )

    private val aiConfigA = BusinessAiConfig(
        workspaceId = workspaceA,
        allowedAutoApprovalRiskThreshold = 30,
        autoApprovalDollarLimit = 2500.0
    )

    // 1. ADMIN can manage users.
    @Test
    fun testAdminCanManageUsers() {
        val result = PermissionEngine.evaluatePermission(
            user = adminUserA,
            targetWorkspaceId = workspaceA,
            action = PermissionAction.MANAGE_USERS
        )
        assertTrue(result is AuthorizationResult.Allowed)
        assertTrue(result.isAllowed)
    }

    // 2. MANAGER cannot modify workspace security settings.
    @Test
    fun testManagerCannotModifyWorkspaceSecuritySettings() {
        val result = PermissionEngine.evaluatePermission(
            user = managerUserA,
            targetWorkspaceId = workspaceA,
            action = PermissionAction.MODIFY_WORKSPACE
        )
        assertFalse(result.isAllowed)
        assertTrue(result is AuthorizationResult.InsufficientRole)
    }

    // 3. EMPLOYEE cannot modify AI governance.
    @Test
    fun testEmployeeCannotModifyAiGovernance() {
        val result = PermissionEngine.evaluatePermission(
            user = employeeUserA,
            targetWorkspaceId = workspaceA,
            action = PermissionAction.MODIFY_AI_GOVERNANCE
        )
        assertFalse(result.isAllowed)
        assertTrue(result is AuthorizationResult.InsufficientRole)
    }

    // 4. AUDITOR cannot modify workspace data.
    @Test
    fun testAuditorCannotModifyWorkspaceData() {
        val resultModifyWs = PermissionEngine.evaluatePermission(
            user = auditorUserA,
            targetWorkspaceId = workspaceA,
            action = PermissionAction.MODIFY_WORKSPACE
        )
        val resultModifyAi = PermissionEngine.evaluatePermission(
            user = auditorUserA,
            targetWorkspaceId = workspaceA,
            action = PermissionAction.MODIFY_AI_CONFIGURATION
        )
        assertFalse(resultModifyWs.isAllowed)
        assertFalse(resultModifyAi.isAllowed)
        assertTrue(resultModifyWs is AuthorizationResult.InsufficientRole)
    }

    // 5. AUDITOR can view audit information.
    @Test
    fun testAuditorCanViewAuditInformation() {
        val result = PermissionEngine.evaluatePermission(
            user = auditorUserA,
            targetWorkspaceId = workspaceA,
            action = PermissionAction.VIEW_AUDIT_LOGS
        )
        assertTrue(result is AuthorizationResult.Allowed)
        assertTrue(result.isAllowed)
    }

    // 6. EMPLOYEE cannot change their own role.
    @Test
    fun testEmployeeCannotChangeTheirOwnRole() {
        val result = PermissionEngine.evaluatePermission(
            user = employeeUserA,
            targetWorkspaceId = workspaceA,
            action = PermissionAction.MODIFY_USER_ROLES,
            targetUserId = employeeUserA.userId
        )
        assertFalse(result.isAllowed)
    }

    // 7. MANAGER cannot elevate their own permissions.
    @Test
    fun testManagerCannotElevateTheirOwnPermissions() {
        val result = PermissionEngine.evaluatePermission(
            user = managerUserA,
            targetWorkspaceId = workspaceA,
            action = PermissionAction.MODIFY_USER_ROLES,
            targetUserId = managerUserA.userId
        )
        assertFalse(result.isAllowed)
        assertTrue(result is AuthorizationResult.GovernanceRestriction || result is AuthorizationResult.InsufficientRole)
    }

    // 8. A user cannot access another workspace they do not belong to.
    @Test
    fun testUserCannotAccessAnotherWorkspace() {
        val result = PermissionEngine.evaluatePermission(
            user = adminUserA,
            targetWorkspaceId = workspaceB,
            action = PermissionAction.VIEW_WORKSPACE
        )
        assertFalse(result.isAllowed)
        assertTrue(result is AuthorizationResult.NotAMember)
    }

    // 9. A permitted workflow can execute.
    @Test
    fun testPermittedWorkflowCanExecute() {
        val result = PermissionEngine.evaluatePermission(
            user = employeeUserA,
            targetWorkspaceId = workspaceA,
            action = PermissionAction.EXECUTE_WORKFLOW,
            riskScorePct = 10,
            dollarAmount = 100.0,
            aiConfig = aiConfigA
        )
        assertTrue(result is AuthorizationResult.Allowed)
    }

    // 10. A restricted workflow is rejected.
    @Test
    fun testRestrictedWorkflowIsRejectedForAuditor() {
        val result = PermissionEngine.evaluatePermission(
            user = auditorUserA,
            targetWorkspaceId = workspaceA,
            action = PermissionAction.EXECUTE_WORKFLOW
        )
        assertFalse(result.isAllowed)
        assertTrue(result is AuthorizationResult.InsufficientRole)
    }

    // 11. An action requiring approval returns "APPROVAL_REQUIRED" rather than silently allowing execution.
    @Test
    fun testActionRequiringApprovalReturnsApprovalRequired() {
        val result = PermissionEngine.evaluatePermission(
            user = employeeUserA,
            targetWorkspaceId = workspaceA,
            action = PermissionAction.EXECUTE_WORKFLOW,
            riskScorePct = 85, // Exceeds threshold of 30%
            dollarAmount = 5000.0, // Exceeds dollar limit of $2500
            aiConfig = aiConfigA
        )
        assertFalse(result.isAllowed)
        assertTrue(result is AuthorizationResult.ApprovalRequired)
        val approvalResult = result as AuthorizationResult.ApprovalRequired
        assertEquals(85, approvalResult.riskScorePct)
        assertEquals(5000.0, approvalResult.dollarAmount, 0.01)
    }

    // 12. Invalid or missing user/workspace context is rejected safely.
    @Test
    fun testMissingUserContextIsRejectedSafely() {
        val result = PermissionEngine.evaluatePermission(
            user = null,
            targetWorkspaceId = workspaceA,
            action = PermissionAction.VIEW_WORKSPACE
        )
        assertFalse(result.isAllowed)
        assertTrue(result is AuthorizationResult.NotAuthenticated)
    }
}
