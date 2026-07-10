package com.enterprise.auth.platform.modules.workflow.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade.EnabledUser;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowDefinition;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowDefinitionStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstance;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstanceStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRepository;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowStepDefinition;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTask;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskStatus;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class WorkflowStore {

    private final WorkflowRepository repository;
    private final UserQueryFacade userQueryFacade;

    WorkflowStore(WorkflowRepository repository, UserQueryFacade userQueryFacade) {
        this.repository = repository;
        this.userQueryFacade = userQueryFacade;
    }

    WorkflowDefinition requireDefinition(String tenantId, Long definitionId) {
        return repository.findDefinition(tenantId, definitionId, TenantContext.isGlobalScope())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "流程定义不存在"));
    }

    WorkflowDefinition requireLatestDeployedDefinition(String tenantId, String definitionKey) {
        if (!StringUtils.hasText(definitionKey)) {
            throw new BusinessException("流程定义键不能为空");
        }
        return repository.findLatestDeployedDefinition(tenantId, definitionKey.trim())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "可发起的流程定义不存在"));
    }

    WorkflowInstance requireInstance(String tenantId, Long instanceId) {
        return repository.findInstance(tenantId, instanceId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "流程实例不存在"));
    }

    WorkflowInstance requireRunningInstance(String tenantId, Long instanceId) {
        WorkflowInstance instance = requireInstance(tenantId, instanceId);
        if (instance.getStatus() != WorkflowInstanceStatus.RUNNING) {
            throw new BusinessException("流程实例不是运行中状态");
        }
        return instance;
    }

    WorkflowTask requireTask(String tenantId, Long taskId) {
        return repository.findTask(tenantId, taskId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "任务不存在"));
    }

    WorkflowTask requirePendingTask(String tenantId, Long taskId) {
        return repository.findPendingTask(tenantId, taskId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "待办任务不存在"));
    }

    EnabledUser requireEnabledUser(String tenantId, Long userId) {
        if (userId == null) {
            throw new BusinessException("目标用户不能为空");
        }
        List<EnabledUser> users = userQueryFacade.findEnabledSummariesByIds(tenantId, Set.of(userId));
        if (users.isEmpty()) {
            throw new BusinessException("NOT_FOUND", "目标用户不存在或已禁用");
        }
        return users.get(0);
    }

    boolean existsBusinessKey(String tenantId, String businessKey) {
        if (!StringUtils.hasText(businessKey)) {
            throw new BusinessException("业务键不能为空");
        }
        return repository.existsBusinessKey(tenantId, businessKey.trim());
    }

    int nextDefinitionVersion(String tenantId, String definitionKey) {
        if (!StringUtils.hasText(definitionKey)) {
            throw new BusinessException("流程定义键不能为空");
        }
        return repository.findLatestDefinition(tenantId, definitionKey.trim())
                .map(WorkflowDefinition::getVersion)
                .map(version -> version + 1)
                .orElse(1);
    }

    WorkflowTask createPendingTask(
            String tenantId,
            WorkflowInstance instance,
            WorkflowDefinition definition,
            WorkflowStepDefinition step,
            int stepIndex
    ) {
        WorkflowTask task = new WorkflowTask();
        task.setTenantId(tenantId);
        task.setInstanceId(instance.getId());
        task.setDefinitionId(definition.getId());
        task.setStepIndex(stepIndex);
        task.setStepName(step.name());
        task.setStatus(WorkflowTaskStatus.PENDING);
        task.setCandidateUserIds(step.candidateUserIds());
        task.setCandidateGroupCodes(step.candidateGroupCodes());
        repository.insertTask(task);
        return task;
    }

    WorkflowTask createStarterReworkTask(
            String tenantId,
            WorkflowInstance instance,
            WorkflowDefinition definition,
            WorkflowStepDefinition rejectedStep
    ) {
        WorkflowTask task = new WorkflowTask();
        task.setTenantId(tenantId);
        task.setInstanceId(instance.getId());
        task.setDefinitionId(definition.getId());
        task.setStepIndex(-1);
        task.setStepName("发起人重提");
        task.setStatus(WorkflowTaskStatus.PENDING);
        task.setCandidateUserIds(Set.of(instance.getStarterUserId()));
        task.setCandidateGroupCodes(Set.of());
        task.setAssigneeUserId(instance.getStarterUserId());
        task.setAssigneeUsername(instance.getStarterUsername());
        task.setComment("由节点「" + rejectedStep.name() + "」驳回发起人重提");
        repository.insertTask(task);
        return task;
    }

    WorkflowTask insertTask(WorkflowTask task) {
        repository.insertTask(task);
        return task;
    }

    void completePendingTask(WorkflowTask task) {
        if (!repository.completePendingTask(task)) {
            throw new BusinessException("CONFLICT", "任务状态已变化，请刷新后重试");
        }
    }

    void updateInstance(WorkflowInstance instance) {
        repository.updateInstance(instance);
    }

    void cancelPendingTasks(String tenantId, Long instanceId, WorkflowTaskStatus status, String comment) {
        if (repository.cancelPendingTasks(tenantId, instanceId, status, comment, TimeSupport.now()) <= 0) {
            throw new BusinessException("CONFLICT", "流程待办状态已变化，请刷新后重试");
        }
    }

    List<WorkflowTask> pendingTasks(String tenantId, Long instanceId) {
        return repository.findPendingTasks(tenantId, instanceId);
    }

    List<WorkflowTask> instanceTasks(String tenantId, Long instanceId) {
        return repository.findInstanceTasks(tenantId, instanceId);
    }

    WorkflowRecipients pendingTaskRecipients(String tenantId, Long instanceId) {
        Set<Long> userIds = new LinkedHashSet<>();
        Set<String> roleCodes = new LinkedHashSet<>();
        pendingTasks(tenantId, instanceId).forEach(task -> addTaskRecipients(task, userIds, roleCodes));
        return new WorkflowRecipients(userIds, roleCodes);
    }

    WorkflowRecipients notificationRecipients(WorkflowTask task) {
        Set<Long> userIds = new LinkedHashSet<>();
        Set<String> roleCodes = new LinkedHashSet<>();
        addTaskRecipients(task, userIds, roleCodes);
        return new WorkflowRecipients(userIds, roleCodes);
    }

    private void addTaskRecipients(WorkflowTask task, Set<Long> userIds, Set<String> roleCodes) {
        if (task == null) {
            return;
        }
        if (task.getAssigneeUserId() != null) {
            userIds.add(task.getAssigneeUserId());
        }
        userIds.addAll(task.getCandidateUserIds());
        roleCodes.addAll(task.getCandidateGroupCodes());
    }

    void ensureActionable(WorkflowTask task, UserAccount user) {
        if (!isActionable(task, user)) {
            throw new BusinessException("ACCESS_DENIED", "当前用户不是该任务候选处理人");
        }
    }

    boolean isActionable(WorkflowTask task, UserAccount user) {
        if (task.getAssigneeUserId() != null) {
            return Objects.equals(task.getAssigneeUserId(), user.id());
        }
        if (task.getCandidateUserIds().contains(user.id())) {
            return true;
        }
        return user.roles().stream().anyMatch(task.getCandidateGroupCodes()::contains);
    }

    boolean canViewInstance(WorkflowInstance instance, UserAccount user) {
        if (Objects.equals(instance.getStarterUserId(), user.id())) {
            return true;
        }
        if (user.permissions().contains(PermissionCodes.WORKFLOW_INSTANCE_GET)) {
            return true;
        }
        return instanceTasks(instance.getTenantId(), instance.getId()).stream()
                .anyMatch(task -> isVisibleTask(task, user));
    }

    private boolean isVisibleTask(WorkflowTask task, UserAccount user) {
        if (Objects.equals(task.getAssigneeUserId(), user.id())) {
            return true;
        }
        if (task.getStatus() == WorkflowTaskStatus.PENDING && isActionable(task, user)) {
            return true;
        }
        if (task.getCandidateUserIds().contains(user.id())) {
            return true;
        }
        return user.roles().stream().anyMatch(task.getCandidateGroupCodes()::contains);
    }
}
