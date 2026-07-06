package com.enterprise.auth.platform.modules.workflow.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade.EnabledUser;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowDefinitionStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstanceStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskStatus;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessDefinitionEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessInstanceEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfProcessDefinitionMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfProcessInstanceMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 工作流持久化与领域数据访问：实体加载守卫、任务生命周期（创建/完成/取消）、
 * 候选人收件人聚合、候选处理人判定与实例可见性判定。
 * 封装对 workflow 三张表的 Mapper 访问，供各工作流应用服务共享。
 */
@Component
class WorkflowStore {

    private final WfProcessDefinitionMapper definitionMapper;
    private final WfProcessInstanceMapper instanceMapper;
    private final WfTaskMapper taskMapper;
    private final UserQueryFacade userQueryFacade;
    private final WorkflowCodec codec;

    WorkflowStore(
            WfProcessDefinitionMapper definitionMapper,
            WfProcessInstanceMapper instanceMapper,
            WfTaskMapper taskMapper,
            UserQueryFacade userQueryFacade,
            WorkflowCodec codec
    ) {
        this.definitionMapper = definitionMapper;
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.userQueryFacade = userQueryFacade;
        this.codec = codec;
    }

    // ---------- 加载守卫 ----------

    WfProcessDefinitionEntity requireDefinition(String tenantId, Long definitionId) {
        WfProcessDefinitionEntity entity = definitionMapper.selectOne(new LambdaQueryWrapper<WfProcessDefinitionEntity>()
                .eq(!TenantContext.isGlobalScope(), WfProcessDefinitionEntity::getTenantId, tenantId)
                .eq(WfProcessDefinitionEntity::getId, definitionId)
                .eq(WfProcessDefinitionEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("NOT_FOUND", "流程定义不存在");
        }
        return entity;
    }

    WfProcessDefinitionEntity requireLatestDeployedDefinition(String tenantId, String definitionKey) {
        if (!StringUtils.hasText(definitionKey)) {
            throw new BusinessException("流程定义键不能为空");
        }
        WfProcessDefinitionEntity entity = definitionMapper.selectOne(new LambdaQueryWrapper<WfProcessDefinitionEntity>()
                .eq(WfProcessDefinitionEntity::getTenantId, tenantId)
                .eq(WfProcessDefinitionEntity::getDefinitionKey, definitionKey.trim())
                .eq(WfProcessDefinitionEntity::getStatus, WorkflowDefinitionStatus.DEPLOYED.name())
                .eq(WfProcessDefinitionEntity::getDeleted, 0)
                .orderByDesc(WfProcessDefinitionEntity::getVersion)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("NOT_FOUND", "可发起的流程定义不存在");
        }
        return entity;
    }

    WfProcessInstanceEntity requireInstance(String tenantId, Long instanceId) {
        WfProcessInstanceEntity entity = instanceMapper.selectOne(new LambdaQueryWrapper<WfProcessInstanceEntity>()
                .eq(WfProcessInstanceEntity::getTenantId, tenantId)
                .eq(WfProcessInstanceEntity::getId, instanceId)
                .eq(WfProcessInstanceEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("NOT_FOUND", "流程实例不存在");
        }
        return entity;
    }

    WfProcessInstanceEntity requireRunningInstance(String tenantId, Long instanceId) {
        WfProcessInstanceEntity instance = requireInstance(tenantId, instanceId);
        if (!WorkflowInstanceStatus.RUNNING.name().equals(instance.getStatus())) {
            throw new BusinessException("流程实例不是运行中状态");
        }
        return instance;
    }

    WfTaskEntity requirePendingTask(String tenantId, Long taskId) {
        WfTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getTenantId, tenantId)
                .eq(WfTaskEntity::getId, taskId)
                .eq(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                .eq(WfTaskEntity::getDeleted, 0)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException("NOT_FOUND", "待办任务不存在");
        }
        return task;
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
        return instanceMapper.selectCount(new LambdaQueryWrapper<WfProcessInstanceEntity>()
                .eq(WfProcessInstanceEntity::getTenantId, tenantId)
                .eq(WfProcessInstanceEntity::getBusinessKey, businessKey.trim())
                .eq(WfProcessInstanceEntity::getDeleted, 0)) > 0;
    }

    int nextDefinitionVersion(String tenantId, String definitionKey) {
        if (!StringUtils.hasText(definitionKey)) {
            throw new BusinessException("流程定义键不能为空");
        }
        WfProcessDefinitionEntity latest = definitionMapper.selectOne(new LambdaQueryWrapper<WfProcessDefinitionEntity>()
                .eq(WfProcessDefinitionEntity::getTenantId, tenantId)
                .eq(WfProcessDefinitionEntity::getDefinitionKey, definitionKey.trim())
                .eq(WfProcessDefinitionEntity::getDeleted, 0)
                .orderByDesc(WfProcessDefinitionEntity::getVersion)
                .last("limit 1"));
        return latest == null ? 1 : latest.getVersion() + 1;
    }

    // ---------- 任务生命周期 ----------

    WfTaskEntity createPendingTask(
            String tenantId,
            WfProcessInstanceEntity instance,
            WfProcessDefinitionEntity definition,
            WorkflowStepDefinition step,
            int stepIndex
    ) {
        WfTaskEntity task = new WfTaskEntity();
        task.setTenantId(tenantId);
        task.setInstanceId(instance.getId());
        task.setDefinitionId(definition.getId());
        task.setStepIndex(stepIndex);
        task.setStepName(step.name());
        task.setStatus(WorkflowTaskStatus.PENDING.name());
        task.setCandidateUserIdsJson(codec.toJson(step.candidateUserIds()));
        task.setCandidateGroupCodesJson(codec.toJson(step.candidateGroupCodes()));
        taskMapper.insert(task);
        return task;
    }

    WfTaskEntity createStarterReworkTask(
            String tenantId,
            WfProcessInstanceEntity instance,
            WfProcessDefinitionEntity definition,
            WorkflowStepDefinition rejectedStep
    ) {
        WfTaskEntity task = new WfTaskEntity();
        task.setTenantId(tenantId);
        task.setInstanceId(instance.getId());
        task.setDefinitionId(definition.getId());
        task.setStepIndex(-1);
        task.setStepName("发起人重提");
        task.setStatus(WorkflowTaskStatus.PENDING.name());
        task.setCandidateUserIdsJson(codec.toJson(Set.of(instance.getStarterUserId())));
        task.setCandidateGroupCodesJson(codec.toJson(Set.of()));
        task.setAssigneeUserId(instance.getStarterUserId());
        task.setAssigneeUsername(instance.getStarterUsername());
        task.setComment("由节点「" + rejectedStep.name() + "」驳回发起人重提");
        taskMapper.insert(task);
        return task;
    }

    WfTaskEntity insertTask(WfTaskEntity task) {
        taskMapper.insert(task);
        return task;
    }

    void completePendingTask(WfTaskEntity task) {
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getTenantId, task.getTenantId())
                .eq(WfTaskEntity::getId, task.getId())
                .eq(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                .eq(WfTaskEntity::getDeleted, 0)
                .set(WfTaskEntity::getStatus, task.getStatus())
                .set(WfTaskEntity::getAssigneeUserId, task.getAssigneeUserId())
                .set(WfTaskEntity::getAssigneeUsername, task.getAssigneeUsername())
                .set(WfTaskEntity::getComment, task.getComment())
                .set(WfTaskEntity::getCompletedAt, task.getCompletedAt()));
        if (updated <= 0) {
            throw new BusinessException("CONFLICT", "任务状态已变化，请刷新后重试");
        }
    }

    void cancelPendingTasks(String tenantId, Long instanceId, WorkflowTaskStatus status, String comment) {
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getTenantId, tenantId)
                .eq(WfTaskEntity::getInstanceId, instanceId)
                .eq(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                .eq(WfTaskEntity::getDeleted, 0)
                .set(WfTaskEntity::getStatus, status.name())
                .set(WfTaskEntity::getComment, comment)
                .set(WfTaskEntity::getCompletedAt, TimeSupport.now()));
        if (updated <= 0) {
            throw new BusinessException("CONFLICT", "流程待办状态已变化，请刷新后重试");
        }
    }

    List<WfTaskEntity> pendingTasks(String tenantId, Long instanceId) {
        return taskMapper.selectList(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getTenantId, tenantId)
                .eq(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                .eq(WfTaskEntity::getDeleted, 0)
                .eq(WfTaskEntity::getInstanceId, instanceId));
    }

    List<WfTaskEntity> instanceTasks(String tenantId, Long instanceId) {
        return taskMapper.selectList(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getTenantId, tenantId)
                .eq(WfTaskEntity::getInstanceId, instanceId)
                .eq(WfTaskEntity::getDeleted, 0));
    }

    // ---------- 收件人聚合 ----------

    WorkflowRecipients pendingTaskRecipients(String tenantId, Long instanceId) {
        Set<Long> userIds = new LinkedHashSet<>();
        Set<String> roleCodes = new LinkedHashSet<>();
        pendingTasks(tenantId, instanceId).forEach(task -> addTaskRecipients(task, userIds, roleCodes));
        return new WorkflowRecipients(userIds, roleCodes);
    }

    WorkflowRecipients notificationRecipients(WfTaskEntity task) {
        Set<Long> userIds = new LinkedHashSet<>();
        Set<String> roleCodes = new LinkedHashSet<>();
        addTaskRecipients(task, userIds, roleCodes);
        return new WorkflowRecipients(userIds, roleCodes);
    }

    private void addTaskRecipients(WfTaskEntity task, Set<Long> userIds, Set<String> roleCodes) {
        if (task == null) {
            return;
        }
        if (task.getAssigneeUserId() != null) {
            userIds.add(task.getAssigneeUserId());
        }
        userIds.addAll(codec.readLongSet(task.getCandidateUserIdsJson()));
        roleCodes.addAll(codec.readStringSet(task.getCandidateGroupCodesJson()));
    }

    // ---------- 候选处理人与可见性判定 ----------

    void ensureActionable(WfTaskEntity task, UserAccount user) {
        if (!isActionable(task, user)) {
            throw new BusinessException("ACCESS_DENIED", "当前用户不是该任务候选处理人");
        }
    }

    boolean isActionable(WfTaskEntity task, UserAccount user) {
        if (task.getAssigneeUserId() != null) {
            return Objects.equals(task.getAssigneeUserId(), user.id());
        }
        Set<Long> candidateUserIds = codec.readLongSet(task.getCandidateUserIdsJson());
        if (candidateUserIds.contains(user.id())) {
            return true;
        }
        Set<String> candidateGroupCodes = codec.readStringSet(task.getCandidateGroupCodesJson());
        return user.roles().stream().anyMatch(candidateGroupCodes::contains);
    }

    boolean canViewInstance(WfProcessInstanceEntity instance, UserAccount user) {
        if (Objects.equals(instance.getStarterUserId(), user.id())) {
            return true;
        }
        if (user.permissions().contains(PermissionCodes.WORKFLOW_INSTANCE_GET)) {
            return true;
        }
        return hasVisibleTask(instance, user);
    }

    private boolean hasVisibleTask(WfProcessInstanceEntity instance, UserAccount user) {
        return instanceTasks(instance.getTenantId(), instance.getId())
                .stream()
                .anyMatch(task -> {
                    if (Objects.equals(task.getAssigneeUserId(), user.id())) {
                        return true;
                    }
                    if (WorkflowTaskStatus.PENDING.name().equals(task.getStatus()) && isActionable(task, user)) {
                        return true;
                    }
                    Set<Long> candidateUserIds = codec.readLongSet(task.getCandidateUserIdsJson());
                    if (candidateUserIds.contains(user.id())) {
                        return true;
                    }
                    Set<String> candidateGroupCodes = codec.readStringSet(task.getCandidateGroupCodesJson());
                    return user.roles().stream().anyMatch(candidateGroupCodes::contains);
                });
    }
}
