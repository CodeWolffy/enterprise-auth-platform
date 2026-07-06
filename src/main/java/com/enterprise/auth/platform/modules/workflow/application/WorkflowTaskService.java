package com.enterprise.auth.platform.modules.workflow.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade.EnabledUser;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstanceStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRejectResolver;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskStatus;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessDefinitionEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessInstanceEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfProcessInstanceMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskMapper;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 流程任务应用服务：审批通过、驳回（含驳回策略路由）、转签，以及我的待办/已办查询。
 * 驳回后的目标节点由领域状态机 {@link WorkflowRejectResolver} 计算。
 */
@Service
public class WorkflowTaskService {

    private final WfTaskMapper taskMapper;
    private final WfProcessInstanceMapper instanceMapper;
    private final WorkflowStore store;
    private final WorkflowViewMapper viewMapper;
    private final WorkflowNotifier notifier;
    private final WorkflowCodec codec;
    private final CurrentUserService currentUserService;

    public WorkflowTaskService(
            WfTaskMapper taskMapper,
            WfProcessInstanceMapper instanceMapper,
            WorkflowStore store,
            WorkflowViewMapper viewMapper,
            WorkflowNotifier notifier,
            WorkflowCodec codec,
            CurrentUserService currentUserService
    ) {
        this.taskMapper = taskMapper;
        this.instanceMapper = instanceMapper;
        this.store = store;
        this.viewMapper = viewMapper;
        this.notifier = notifier;
        this.codec = codec;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public WorkflowActionResult approveTask(Long taskId, WorkflowTaskCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WfTaskEntity task = store.requirePendingTask(tenantId, taskId);
        WfProcessInstanceEntity instance = store.requireRunningInstance(tenantId, task.getInstanceId());
        store.ensureActionable(task, user);
        WfProcessDefinitionEntity definition = store.requireDefinition(tenantId, task.getDefinitionId());
        List<WorkflowStepDefinition> steps = codec.readSteps(definition.getStepsJson());

        task.setStatus(WorkflowTaskStatus.APPROVED.name());
        task.setAssigneeUserId(user.id());
        task.setAssigneeUsername(user.username());
        task.setComment(WorkflowSupport.normalizeText(command.comment()));
        task.setCompletedAt(TimeSupport.now());
        store.completePendingTask(task);

        WorkflowTaskView nextTask = null;
        WfTaskEntity nextTaskEntity = null;
        int nextStepIndex = task.getStepIndex() < 0 ? 0 : task.getStepIndex() + 1;
        if (nextStepIndex >= steps.size()) {
            instance.setStatus(WorkflowInstanceStatus.APPROVED.name());
            instance.setEndedAt(TimeSupport.now());
        } else {
            instance.setCurrentStepIndex(nextStepIndex);
            nextTaskEntity = store.createPendingTask(tenantId, instance, definition, steps.get(nextStepIndex), nextStepIndex);
            nextTask = viewMapper.toTaskView(nextTaskEntity, user);
        }
        instanceMapper.updateById(instance);
        notifier.publishWorkflowTaskDecision(tenantId, instance, task, user.username(), true);
        if (nextTaskEntity != null) {
            notifier.publishWorkflowTodoCreated(tenantId, instance, nextTaskEntity, user.username());
        }
        return new WorkflowActionResult(viewMapper.toInstanceView(instance), nextTask);
    }

    @Transactional
    public WorkflowActionResult rejectTask(Long taskId, WorkflowTaskCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WfTaskEntity task = store.requirePendingTask(tenantId, taskId);
        WfProcessInstanceEntity instance = store.requireRunningInstance(tenantId, task.getInstanceId());
        store.ensureActionable(task, user);
        WfProcessDefinitionEntity definition = store.requireDefinition(tenantId, task.getDefinitionId());
        List<WorkflowStepDefinition> steps = codec.readSteps(definition.getStepsJson());
        WorkflowStepDefinition currentStep = WorkflowSupport.stepAt(steps, task.getStepIndex());

        int nextStepIndex = WorkflowRejectResolver.resolveTarget(
                currentStep.rejectStrategy(), currentStep.rejectTarget(), task.getStepIndex(), steps.size());

        task.setStatus(WorkflowTaskStatus.REJECTED.name());
        task.setAssigneeUserId(user.id());
        task.setAssigneeUsername(user.username());
        task.setComment(WorkflowSupport.normalizeText(command.comment()));
        task.setCompletedAt(TimeSupport.now());
        store.completePendingTask(task);

        WorkflowTaskView nextTask = null;
        WfTaskEntity nextTaskEntity = null;
        if (nextStepIndex == WorkflowRejectResolver.TARGET_END) {
            instance.setStatus(WorkflowInstanceStatus.REJECTED.name());
            instance.setEndedAt(TimeSupport.now());
        } else if (nextStepIndex == WorkflowRejectResolver.TARGET_STARTER) {
            instance.setCurrentStepIndex(-1);
            nextTaskEntity = store.createStarterReworkTask(tenantId, instance, definition, currentStep);
            nextTask = viewMapper.toTaskView(nextTaskEntity, user);
        } else {
            instance.setCurrentStepIndex(nextStepIndex);
            nextTaskEntity = store.createPendingTask(tenantId, instance, definition, steps.get(nextStepIndex), nextStepIndex);
            nextTask = viewMapper.toTaskView(nextTaskEntity, user);
        }
        instanceMapper.updateById(instance);
        notifier.publishWorkflowTaskDecision(tenantId, instance, task, user.username(), false);
        if (nextTaskEntity != null) {
            notifier.publishWorkflowTodoCreated(tenantId, instance, nextTaskEntity, user.username());
        }
        return new WorkflowActionResult(viewMapper.toInstanceView(instance), nextTask);
    }

    @Transactional
    public WorkflowActionResult transferTask(Long taskId, WorkflowTaskTransferCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WfTaskEntity task = store.requirePendingTask(tenantId, taskId);
        WfProcessInstanceEntity instance = store.requireRunningInstance(tenantId, task.getInstanceId());
        store.ensureActionable(task, user);
        EnabledUser targetUser = store.requireEnabledUser(tenantId, command.targetUserId());
        if (Objects.equals(targetUser.id(), user.id())) {
            throw new BusinessException("不能转签给自己");
        }

        Instant now = TimeSupport.now();
        task.setStatus(WorkflowTaskStatus.TRANSFERRED.name());
        task.setAssigneeUserId(user.id());
        task.setAssigneeUsername(user.username());
        task.setComment(WorkflowSupport.normalizeText(command.comment()));
        task.setCompletedAt(now);
        store.completePendingTask(task);

        WfTaskEntity transferredTask = new WfTaskEntity();
        transferredTask.setTenantId(tenantId);
        transferredTask.setInstanceId(task.getInstanceId());
        transferredTask.setDefinitionId(task.getDefinitionId());
        transferredTask.setStepIndex(task.getStepIndex());
        transferredTask.setStepName(task.getStepName());
        transferredTask.setStatus(WorkflowTaskStatus.PENDING.name());
        transferredTask.setCandidateUserIdsJson(codec.toJson(Set.of(targetUser.id())));
        transferredTask.setCandidateGroupCodesJson(codec.toJson(Set.of()));
        transferredTask.setAssigneeUserId(targetUser.id());
        transferredTask.setAssigneeUsername(targetUser.username());
        transferredTask.setComment("由 " + user.username() + " 转签");
        store.insertTask(transferredTask);

        notifier.publishWorkflowTaskTransferred(tenantId, instance, task, transferredTask, targetUser, user.username());
        notifier.publishWorkflowTodoCreated(tenantId, instance, transferredTask, user.username());
        return new WorkflowActionResult(viewMapper.toInstanceView(instance), viewMapper.toTaskView(transferredTask, user));
    }

    public PageResult<WorkflowTaskView> todoTasks(int page, int size) {
        return todoTasks(page, size, null);
    }

    public PageResult<WorkflowTaskView> todoTasks(int page, int size, Long taskId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        LambdaQueryWrapper<WfTaskEntity> wrapper = new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getTenantId, tenantId)
                .eq(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                .eq(WfTaskEntity::getDeleted, 0);
        if (taskId != null && taskId > 0) {
            wrapper.eq(WfTaskEntity::getId, taskId);
        }
        // 下推候选人过滤到 SQL 层：仅拉取指派给当前用户或尚未指派的任务
        // 避免全量 PENDING 任务加载到内存再过滤
        wrapper.and(w -> w.eq(WfTaskEntity::getAssigneeUserId, user.id())
                .or()
                .isNull(WfTaskEntity::getAssigneeUserId));
        // 加硬上限防止意外全量加载，正常场景下候选人匹配后的有效数据远小于此值
        wrapper.last("LIMIT 500");
        List<WorkflowTaskView> filtered = taskMapper.selectList(wrapper
                        .orderByAsc(WfTaskEntity::getCreatedAt)
                        .orderByAsc(WfTaskEntity::getId))
                .stream()
                .filter(task -> store.isActionable(task, user))
                .map(task -> viewMapper.toTaskView(task, user))
                .toList();
        return WorkflowSupport.page(filtered, page, size);
    }

    public PageResult<WorkflowTaskView> doneTasks(int page, int size) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        List<WorkflowTaskView> filtered = taskMapper.selectList(new LambdaQueryWrapper<WfTaskEntity>()
                        .eq(WfTaskEntity::getTenantId, tenantId)
                        .ne(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                        .eq(WfTaskEntity::getAssigneeUserId, user.id())
                        .eq(WfTaskEntity::getDeleted, 0)
                        .orderByDesc(WfTaskEntity::getCompletedAt)
                        .orderByDesc(WfTaskEntity::getId))
                .stream()
                .map(task -> viewMapper.toTaskView(task, user))
                .toList();
        return WorkflowSupport.page(filtered, page, size);
    }
}
