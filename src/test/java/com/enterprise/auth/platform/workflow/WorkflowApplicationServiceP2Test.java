package com.enterprise.auth.platform.workflow;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bind;
import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.clear;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.workflow.application.WorkflowApplicationService;
import com.enterprise.auth.platform.modules.workflow.application.WorkflowDefinitionCommand;
import com.enterprise.auth.platform.modules.workflow.application.WorkflowStartCommand;
import com.enterprise.auth.platform.modules.workflow.application.WorkflowStepDefinition;
import com.enterprise.auth.platform.modules.workflow.application.WorkflowTaskCommand;
import com.enterprise.auth.platform.modules.workflow.application.WorkflowTaskUrgeService;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRejectStrategy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class WorkflowApplicationServiceP2Test {

    private static final String TENANT_A = "p2-wf-tenant-a";
    private static final String TENANT_B = "p2-wf-tenant-b";
    private static final String STARTER = "p2_wf_starter_ut";
    private static final String APPROVER_ONE = "p2_wf_approver_one_ut";
    private static final String APPROVER_TWO = "p2_wf_approver_two_ut";
    private static final String OUTSIDER = "p2_wf_outsider_ut";
    private static final String OTHER_TENANT_USER = "p2_wf_other_tenant_ut";
    private static final String GROUP_APPROVER = "P2_WF_GROUP_APPROVER_UT";
    private static final String DEFINITION_KEY = "p2-wf-poc-ut";

    @Autowired
    private WorkflowApplicationService workflowApplicationService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkflowTaskUrgeService urgeService;

    private Long starterId;
    private Long approverOneId;
    private Long approverTwoId;
    private Long outsiderId;
    private Long otherTenantUserId;

    @BeforeEach
    void setUp() {
        cleanup();
        starterId = ensureUser(TENANT_A, STARTER);
        approverOneId = ensureUser(TENANT_A, APPROVER_ONE);
        approverTwoId = ensureUser(TENANT_A, APPROVER_TWO);
        outsiderId = ensureUser(TENANT_A, OUTSIDER);
        otherTenantUserId = ensureUser(TENANT_B, OTHER_TENANT_USER);
        ensureRole(TENANT_A, GROUP_APPROVER);
        ensureRole(TENANT_B, GROUP_APPROVER);
    }

    @AfterEach
    void tearDown() {
        clear();
        TenantContext.clear();
        cleanup();
    }

    @Test
    void sequentialApprovalShouldAdvanceAndCompleteWithVariableSnapshot() {
        bindPrincipal(TENANT_A, starterId, STARTER, Set.of(), Set.of("upms:workflowtodo:edit", "upms:workflowtodo:get"));
        Long definitionId = createAndDeployTwoStepDefinition();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("amount", 100);
        variables.put("reason", "p2-poc");

        var started = workflowApplicationService.startInstance(new WorkflowStartCommand(
                DEFINITION_KEY,
                "p2-business-sequential",
                "P2 PoC 顺序审批",
                variables
        ));
        variables.put("amount", 999);

        assertThat(started.instance().definitionId()).isEqualTo(definitionId);
        assertThat(started.instance().status()).isEqualTo("RUNNING");
        assertThat(started.instance().variablesSnapshot()).containsEntry("amount", 100);
        assertThat(started.instance().variablesSnapshot()).containsEntry("reason", "p2-poc");
        assertThat(started.currentTask().candidateUserIds()).containsExactly(approverOneId);

        bindPrincipal(TENANT_A, approverOneId, APPROVER_ONE, Set.of(), Set.of());
        assertThat(workflowApplicationService.todoTasks(1, 20).records())
                .extracting("id")
                .contains(started.currentTask().id());
        var firstApproved = workflowApplicationService.approveTask(started.currentTask().id(), new WorkflowTaskCommand("同意"));

        assertThat(firstApproved.instance().status()).isEqualTo("RUNNING");
        assertThat(firstApproved.instance().currentStepIndex()).isEqualTo(1);
        assertThat(firstApproved.nextTask()).isNotNull();
        assertThat(firstApproved.nextTask().candidateGroupCodes()).containsExactly(GROUP_APPROVER);

        bindPrincipal(TENANT_A, approverTwoId, APPROVER_TWO, Set.of(GROUP_APPROVER), Set.of());
        assertThat(workflowApplicationService.todoTasks(1, 20).records())
                .extracting("id")
                .contains(firstApproved.nextTask().id());
        var finalApproved = workflowApplicationService.approveTask(firstApproved.nextTask().id(), new WorkflowTaskCommand("复核通过"));

        assertThat(finalApproved.instance().status()).isEqualTo("APPROVED");
        assertThat(finalApproved.nextTask()).isNull();
        assertThat(workflowApplicationService.doneTasks(1, 20).records())
                .extracting("status")
                .contains("APPROVED");
        assertWorkflowAuditRecorded("WORKFLOW_TASK_APPROVED", TENANT_A);
    }

    @Test
    void rejectShouldEndInstanceAndRemoveTodo() {
        bindPrincipal(TENANT_A, starterId, STARTER, Set.of(), Set.of("upms:workflowtodo:edit"));
        createAndDeploySingleStepDefinition("p2-wf-reject-ut", Set.of(approverOneId), Set.of());
        var started = workflowApplicationService.startInstance(new WorkflowStartCommand(
                "p2-wf-reject-ut",
                "p2-business-reject",
                "P2 PoC 驳回",
                Map.of("amount", 200)
        ));

        bindPrincipal(TENANT_A, approverOneId, APPROVER_ONE, Set.of(), Set.of());
        var rejected = workflowApplicationService.rejectTask(started.currentTask().id(), new WorkflowTaskCommand("资料不完整"));

        assertThat(rejected.instance().status()).isEqualTo("REJECTED");
        assertThat(rejected.nextTask()).isNull();
        assertThat(workflowApplicationService.todoTasks(1, 20).records()).isEmpty();
        assertWorkflowAuditRecorded("WORKFLOW_TASK_REJECTED", TENANT_A);
    }

    @Test
    void rejectToStepShouldReturnPreviousConfiguredNode() {
        bindPrincipal(TENANT_A, starterId, STARTER, Set.of(), Set.of("upms:workflowtodo:edit"));
        var definition = workflowApplicationService.createDefinition(new WorkflowDefinitionCommand(
                "p2-wf-reject-to-step-ut",
                "P2 指定节点驳回",
                List.of(
                        new WorkflowStepDefinition("直属审批", Set.of(approverOneId), Set.of()),
                        new WorkflowStepDefinition("角色复核", Set.of(), Set.of(GROUP_APPROVER), WorkflowRejectStrategy.TO_STEP, 0)
                ),
                null
        ));
        workflowApplicationService.deployDefinition(definition.id());
        var started = workflowApplicationService.startInstance(new WorkflowStartCommand(
                "p2-wf-reject-to-step-ut",
                "p2-business-reject-to-step",
                "P2 指定节点驳回",
                Map.of("amount", 700)
        ));

        bindPrincipal(TENANT_A, approverOneId, APPROVER_ONE, Set.of(), Set.of());
        var firstApproved = workflowApplicationService.approveTask(started.currentTask().id(), new WorkflowTaskCommand("同意"));

        bindPrincipal(TENANT_A, approverTwoId, APPROVER_TWO, Set.of(GROUP_APPROVER), Set.of());
        var rejected = workflowApplicationService.rejectTask(firstApproved.nextTask().id(), new WorkflowTaskCommand("回到直属审批"));

        assertThat(rejected.instance().status()).isEqualTo("RUNNING");
        assertThat(rejected.instance().currentStepIndex()).isEqualTo(0);
        assertThat(rejected.nextTask()).isNotNull();
        assertThat(rejected.nextTask().stepIndex()).isEqualTo(0);
        assertThat(rejected.nextTask().candidateUserIds()).containsExactly(approverOneId);
    }

    @Test
    void rejectToStarterShouldCreateStarterReworkTaskAndRestartAfterSubmit() {
        bindPrincipal(TENANT_A, starterId, STARTER, Set.of(), Set.of("upms:workflowtodo:edit"));
        var definition = workflowApplicationService.createDefinition(new WorkflowDefinitionCommand(
                "p2-wf-reject-to-starter-ut",
                "P2 发起人重提",
                List.of(new WorkflowStepDefinition("直属审批", Set.of(approverOneId), Set.of(), WorkflowRejectStrategy.TO_STARTER)),
                null
        ));
        workflowApplicationService.deployDefinition(definition.id());
        var started = workflowApplicationService.startInstance(new WorkflowStartCommand(
                "p2-wf-reject-to-starter-ut",
                "p2-business-reject-to-starter",
                "P2 发起人重提",
                Map.of("amount", 800)
        ));

        bindPrincipal(TENANT_A, approverOneId, APPROVER_ONE, Set.of(), Set.of());
        var rejected = workflowApplicationService.rejectTask(started.currentTask().id(), new WorkflowTaskCommand("请补充材料"));

        assertThat(rejected.instance().status()).isEqualTo("RUNNING");
        assertThat(rejected.instance().currentStepIndex()).isEqualTo(-1);
        assertThat(rejected.nextTask()).isNotNull();
        assertThat(rejected.nextTask().stepIndex()).isEqualTo(-1);
        assertThat(rejected.nextTask().assigneeUserId()).isEqualTo(starterId);

        bindPrincipal(TENANT_A, starterId, STARTER, Set.of(), Set.of());
        var resubmitted = workflowApplicationService.approveTask(rejected.nextTask().id(), new WorkflowTaskCommand("已补充材料"));

        assertThat(resubmitted.instance().status()).isEqualTo("RUNNING");
        assertThat(resubmitted.instance().currentStepIndex()).isEqualTo(0);
        assertThat(resubmitted.nextTask()).isNotNull();
        assertThat(resubmitted.nextTask().stepIndex()).isEqualTo(0);
        assertThat(resubmitted.nextTask().candidateUserIds()).containsExactly(approverOneId);
    }

    @Test
    void starterShouldWithdrawRunningInstanceAndCancelPendingTask() {
        bindPrincipal(TENANT_A, starterId, STARTER, Set.of(), Set.of("upms:workflowtodo:edit"));
        createAndDeploySingleStepDefinition("p2-wf-withdraw-ut", Set.of(approverOneId), Set.of());
        var started = workflowApplicationService.startInstance(new WorkflowStartCommand(
                "p2-wf-withdraw-ut",
                "p2-business-withdraw",
                "P2 PoC 撤回",
                Map.of("amount", 300)
        ));

        bindPrincipal(TENANT_A, approverOneId, APPROVER_ONE, Set.of(), Set.of());
        assertThatThrownBy(() -> workflowApplicationService.withdrawInstance(started.instance().id()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("发起人");

        bindPrincipal(TENANT_A, starterId, STARTER, Set.of(), Set.of());
        var withdrawn = workflowApplicationService.withdrawInstance(started.instance().id());

        assertThat(withdrawn.status()).isEqualTo("WITHDRAWN");
        String taskStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM wf_task WHERE tenant_id = ? AND id = ?",
                String.class,
                TENANT_A,
                started.currentTask().id()
        );
        assertThat(taskStatus).isEqualTo("CANCELLED");
        assertWorkflowAuditRecorded("WORKFLOW_INSTANCE_WITHDRAWN", TENANT_A);
    }

    @Test
    void actionableUserCanTransferTaskToAnotherEnabledUser() {
        bindPrincipal(TENANT_A, starterId, STARTER, Set.of(), Set.of("upms:workflowtodo:edit"));
        createAndDeploySingleStepDefinition("p2-wf-transfer-ut", Set.of(approverOneId), Set.of());
        var started = workflowApplicationService.startInstance(new WorkflowStartCommand(
                "p2-wf-transfer-ut",
                "p2-business-transfer",
                "P2 MVP 转签",
                Map.of("amount", 500)
        ));

        bindPrincipal(TENANT_A, approverOneId, APPROVER_ONE, Set.of(), Set.of());
        var transferred = workflowApplicationService.transferTask(
                started.currentTask().id(),
                new com.enterprise.auth.platform.modules.workflow.application.WorkflowTaskTransferCommand(approverTwoId, "请代处理")
        );

        assertThat(transferred.instance().status()).isEqualTo("RUNNING");
        assertThat(transferred.nextTask()).isNotNull();
        assertThat(transferred.nextTask().assigneeUserId()).isEqualTo(approverTwoId);
        assertThat(transferred.nextTask().candidateUserIds()).containsExactly(approverTwoId);
        assertThat(workflowApplicationService.todoTasks(1, 20).records()).isEmpty();
        String originalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM wf_task WHERE tenant_id = ? AND id = ?",
                String.class,
                TENANT_A,
                started.currentTask().id()
        );
        assertThat(originalStatus).isEqualTo("TRANSFERRED");

        bindPrincipal(TENANT_A, approverTwoId, APPROVER_TWO, Set.of(), Set.of());
        assertThat(workflowApplicationService.todoTasks(1, 20).records())
                .extracting("id")
                .contains(transferred.nextTask().id());
        var approved = workflowApplicationService.approveTask(transferred.nextTask().id(), new WorkflowTaskCommand("代处理通过"));

        assertThat(approved.instance().status()).isEqualTo("APPROVED");
        assertWorkflowAuditRecorded("WORKFLOW_TASK_TRANSFERRED", TENANT_A);
    }

    @Test
    void processedTaskShouldRejectSecondApprovalAttempt() {
        bindPrincipal(TENANT_A, starterId, STARTER, Set.of(), Set.of("upms:workflowtodo:edit"));
        createAndDeploySingleStepDefinition("p2-wf-concurrent-approve-ut", Set.of(approverOneId), Set.of());
        var started = workflowApplicationService.startInstance(new WorkflowStartCommand(
                "p2-wf-concurrent-approve-ut",
                "p2-business-concurrent-approve",
                "P2 并发审批防重",
                Map.of("amount", 600)
        ));

        bindPrincipal(TENANT_A, approverOneId, APPROVER_ONE, Set.of(), Set.of());
        var approved = workflowApplicationService.approveTask(started.currentTask().id(), new WorkflowTaskCommand("第一次通过"));

        assertThat(approved.instance().status()).isEqualTo("APPROVED");
        assertThatThrownBy(() -> workflowApplicationService.approveTask(started.currentTask().id(), new WorkflowTaskCommand("重复通过")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待办任务不存在");
        Long approvedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_task WHERE tenant_id = ? AND instance_id = ? AND status = 'APPROVED'",
                Long.class,
                TENANT_A,
                started.instance().id()
        );
        Long pendingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_task WHERE tenant_id = ? AND instance_id = ? AND status = 'PENDING'",
                Long.class,
                TENANT_A,
                started.instance().id()
        );
        assertThat(approvedCount).isEqualTo(1);
        assertThat(pendingCount).isZero();
    }

    @Test
    void repeatedUrgeForSameTaskShouldBeRateLimited() {
        bindPrincipal(TENANT_A, starterId, STARTER, Set.of(), Set.of("upms:workflowtodo:edit"));
        createAndDeploySingleStepDefinition("p2-wf-urge-rate-limit-ut", Set.of(approverOneId), Set.of());
        var started = workflowApplicationService.startInstance(new WorkflowStartCommand(
                "p2-wf-urge-rate-limit-ut",
                "p2-business-urge-rate-limit",
                "P2 催办频控",
                Map.of("amount", 650)
        ));

        var first = urgeService.urge(started.currentTask().id(), new WorkflowTaskCommand("请尽快处理"));

        assertThat(first.totalUrgeCount()).isEqualTo(1);
        assertThatThrownBy(() -> urgeService.urge(started.currentTask().id(), new WorkflowTaskCommand("再次催办")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("催办过于频繁");
        Long urgeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_task_urge WHERE tenant_id = ? AND task_id = ? AND deleted = 0",
                Long.class,
                TENANT_A,
                started.currentTask().id()
        );
        assertThat(urgeCount).isEqualTo(1);
    }

    @Test
    void tenantAndCandidateIsolationShouldBlockCrossScopeAccess() {
        bindPrincipal(TENANT_A, starterId, STARTER, Set.of(), Set.of("upms:workflowtodo:edit", "upms:workflowtodo:get"));
        Long definitionId = createAndDeploySingleStepDefinition("p2-wf-isolation-ut", Set.of(approverOneId), Set.of());
        var started = workflowApplicationService.startInstance(new WorkflowStartCommand(
                "p2-wf-isolation-ut",
                "p2-business-isolation",
                "P2 PoC 隔离",
                Map.of("amount", 400)
        ));

        bindPrincipal(TENANT_A, outsiderId, OUTSIDER, Set.of(), Set.of());
        assertThat(workflowApplicationService.todoTasks(1, 20).records()).isEmpty();
        assertThatThrownBy(() -> workflowApplicationService.approveTask(started.currentTask().id(), new WorkflowTaskCommand("越权审批")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("候选处理人");

        bindPrincipal(TENANT_B, otherTenantUserId, OTHER_TENANT_USER, Set.of(GROUP_APPROVER), Set.of("upms:workflowtodo:get"));
        assertThat(workflowApplicationService.definitions(null, 1, 20).records()).isEmpty();
        assertThatThrownBy(() -> workflowApplicationService.definition(definitionId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程定义不存在");
    }

    private Long createAndDeployTwoStepDefinition() {
        var definition = workflowApplicationService.createDefinition(new WorkflowDefinitionCommand(
                DEFINITION_KEY,
                "P2 PoC 审批",
                List.of(
                        new WorkflowStepDefinition("直属审批", Set.of(approverOneId), Set.of()),
                        new WorkflowStepDefinition("角色复核", Set.of(), Set.of(GROUP_APPROVER))
                ),
                "P2 workflow PoC"
        ));
        return workflowApplicationService.deployDefinition(definition.id()).id();
    }

    private Long createAndDeploySingleStepDefinition(String definitionKey, Set<Long> candidateUserIds, Set<String> candidateGroupCodes) {
        var definition = workflowApplicationService.createDefinition(new WorkflowDefinitionCommand(
                definitionKey,
                definitionKey,
                List.of(new WorkflowStepDefinition("单步审批", candidateUserIds, candidateGroupCodes)),
                null
        ));
        return workflowApplicationService.deployDefinition(definition.id()).id();
    }

    private Long ensureUser(String tenantId, String username) {
        TenantContext.setTenantId(tenantId);
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId(tenantId);
        entity.setUsername(username);
        entity.setDisplayName(username);
        entity.setPasswordHash("hash");
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
        return entity.getId();
    }

    private void ensureRole(String tenantId, String roleCode) {
        TenantContext.setTenantId(tenantId);
        SysRoleEntity entity = new SysRoleEntity();
        entity.setTenantId(tenantId);
        entity.setRoleCode(roleCode);
        entity.setRoleName(roleCode);
        entity.setDataScopeType("ALL");
        sysRoleMapper.insert(entity);
    }

    private void bindPrincipal(String tenantId, Long userId, String username, Set<String> roles, Set<String> permissions) {
        TenantContext.setTenantId(tenantId);
        bind(new UserAccount(
                userId,
                tenantId,
                username,
                "hash",
                true,
                roles,
                permissions,
                Set.of(),
                DataScopeType.ALL,
                1
        ));
    }

    private void assertWorkflowAuditRecorded(String eventType, String tenantId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_log WHERE tenant_id = ? AND event_type = ?",
                Long.class,
                tenantId,
                eventType
        );
        assertThat(count).isNotNull().isPositive();
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM wf_task_urge WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM sys_user_notification WHERE tenant_id IN (?, ?) AND source_type = 'WORKFLOW_TASK'", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM wf_task WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM wf_process_instance WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM wf_process_definition WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM sys_log WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id IN (?, ?) AND username IN (?, ?, ?, ?, ?)",
                TENANT_A, TENANT_B, STARTER, APPROVER_ONE, APPROVER_TWO, OUTSIDER, OTHER_TENANT_USER);
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id IN (?, ?) AND role_code = ?", TENANT_A, TENANT_B, GROUP_APPROVER);
    }
}