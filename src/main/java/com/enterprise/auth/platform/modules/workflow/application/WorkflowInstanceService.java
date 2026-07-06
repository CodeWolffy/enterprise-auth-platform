package com.enterprise.auth.platform.modules.workflow.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.web.PaginationSupport;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstanceStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskStatus;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessDefinitionEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessInstanceEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfProcessInstanceMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 流程实例应用服务：发起、详情、我的发起、撤回与终止。
 */
@Service
public class WorkflowInstanceService {

    private final WfProcessInstanceMapper instanceMapper;
    private final WorkflowStore store;
    private final WorkflowViewMapper viewMapper;
    private final WorkflowNotifier notifier;
    private final WorkflowCodec codec;
    private final CurrentUserService currentUserService;

    public WorkflowInstanceService(
            WfProcessInstanceMapper instanceMapper,
            WorkflowStore store,
            WorkflowViewMapper viewMapper,
            WorkflowNotifier notifier,
            WorkflowCodec codec,
            CurrentUserService currentUserService
    ) {
        this.instanceMapper = instanceMapper;
        this.store = store;
        this.viewMapper = viewMapper;
        this.notifier = notifier;
        this.codec = codec;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public WorkflowStartResult startInstance(WorkflowStartCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WfProcessDefinitionEntity definition = store.requireLatestDeployedDefinition(tenantId, command.definitionKey());
        List<WorkflowStepDefinition> steps = codec.readSteps(definition.getStepsJson());
        if (steps.isEmpty()) {
            throw new BusinessException("流程定义没有审批步骤");
        }
        if (store.existsBusinessKey(tenantId, command.businessKey())) {
            throw new BusinessException("CONFLICT", "业务单据已存在流程实例");
        }

        WfProcessInstanceEntity instance = new WfProcessInstanceEntity();
        instance.setTenantId(tenantId);
        instance.setDefinitionId(definition.getId());
        instance.setDefinitionKey(definition.getDefinitionKey());
        instance.setDefinitionVersion(definition.getVersion());
        instance.setBusinessKey(command.businessKey().trim());
        instance.setTitle(command.title().trim());
        instance.setStatus(WorkflowInstanceStatus.RUNNING.name());
        instance.setStarterUserId(user.id());
        instance.setStarterUsername(user.username());
        instance.setCurrentStepIndex(0);
        instance.setVariablesSnapshotJson(codec.toJson(codec.snapshotVariables(command.variables())));
        instance.setStartedAt(TimeSupport.now());
        try {
            instanceMapper.insert(instance);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("CONFLICT", "业务单据已存在流程实例");
        }

        WfTaskEntity task = store.createPendingTask(tenantId, instance, definition, steps.get(0), 0);
        notifier.publishWorkflowTodoCreated(tenantId, instance, task, user.username());
        return new WorkflowStartResult(viewMapper.toInstanceView(instance), viewMapper.toTaskView(task, user));
    }

    public WorkflowInstanceView instance(Long instanceId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WfProcessInstanceEntity instance = store.requireInstance(tenantId, instanceId);
        if (!store.canViewInstance(instance, user)) {
            throw new BusinessException("ACCESS_DENIED", "无权查看该流程实例");
        }
        return viewMapper.toInstanceView(instance);
    }

    public PageResult<WorkflowInstanceView> myInstances(String status, int page, int size) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        int normalizedPage = PaginationSupport.normalizePage(page);
        int normalizedSize = WorkflowSupport.normalizeSize(size);
        LambdaQueryWrapper<WfProcessInstanceEntity> wrapper = new LambdaQueryWrapper<WfProcessInstanceEntity>()
                .eq(WfProcessInstanceEntity::getTenantId, tenantId)
                .eq(WfProcessInstanceEntity::getStarterUserId, user.id())
                .eq(WfProcessInstanceEntity::getDeleted, 0);
        if (StringUtils.hasText(status)) {
            wrapper.eq(WfProcessInstanceEntity::getStatus, status.trim().toUpperCase());
        }
        long total = instanceMapper.selectCount(wrapper);
        int offset = (normalizedPage - 1) * normalizedSize;
        wrapper.orderByDesc(WfProcessInstanceEntity::getStartedAt)
                .orderByDesc(WfProcessInstanceEntity::getId)
                .last("limit " + offset + "," + normalizedSize);
        List<WorkflowInstanceView> records = instanceMapper.selectList(wrapper).stream()
                .map(viewMapper::toInstanceView)
                .toList();
        return PageResult.of(total, normalizedPage, normalizedSize, records);
    }

    @Transactional
    public WorkflowInstanceView withdrawInstance(Long instanceId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WfProcessInstanceEntity instance = store.requireRunningInstance(tenantId, instanceId);
        if (!Objects.equals(instance.getStarterUserId(), user.id())) {
            throw new BusinessException("ACCESS_DENIED", "只有发起人可以撤回流程");
        }
        WorkflowRecipients recipients = store.pendingTaskRecipients(tenantId, instance.getId());
        store.cancelPendingTasks(tenantId, instance.getId(), WorkflowTaskStatus.CANCELLED, "发起人撤回");
        instance.setStatus(WorkflowInstanceStatus.WITHDRAWN.name());
        instance.setEndedAt(TimeSupport.now());
        instanceMapper.updateById(instance);
        notifier.publishWorkflowInstanceClosed(tenantId, instance, recipients, user.id(), user.username(), true);
        return viewMapper.toInstanceView(instance);
    }

    @Transactional
    public WorkflowInstanceView terminateInstance(Long instanceId, WorkflowTaskCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WfProcessInstanceEntity instance = store.requireRunningInstance(tenantId, instanceId);
        WorkflowRecipients recipients = store.pendingTaskRecipients(tenantId, instance.getId());
        store.cancelPendingTasks(tenantId, instance.getId(), WorkflowTaskStatus.CANCELLED, WorkflowSupport.normalizeText(command.comment()));
        instance.setStatus(WorkflowInstanceStatus.TERMINATED.name());
        instance.setEndedAt(TimeSupport.now());
        instanceMapper.updateById(instance);
        notifier.publishWorkflowInstanceClosed(tenantId, instance, recipients, user.id(), user.username(), false);
        return viewMapper.toInstanceView(instance);
    }
}
