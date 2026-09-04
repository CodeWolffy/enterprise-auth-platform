package com.enterprise.auth.platform.modules.workflow.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.web.PaginationSupport;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowDefinition;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstance;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstanceStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRepository;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowStepDefinition;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTask;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskStatus;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowInstanceService {

    private final WorkflowRepository repository;
    private final WorkflowStore store;
    private final WorkflowViewMapper viewMapper;
    private final WorkflowNotifier notifier;
    private final WorkflowCodec codec;
    private final CurrentUserService currentUserService;

    public WorkflowInstanceService(
            WorkflowRepository repository,
            WorkflowStore store,
            WorkflowViewMapper viewMapper,
            WorkflowNotifier notifier,
            WorkflowCodec codec,
            CurrentUserService currentUserService
    ) {
        this.repository = repository;
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
        WorkflowDefinition definition = store.requireLatestDeployedDefinition(tenantId, command.definitionKey());
        List<WorkflowStepDefinition> steps = definition.getSteps();
        if (steps.isEmpty()) {
            throw new BusinessException("流程定义没有审批步骤");
        }
        if (store.existsBusinessKey(tenantId, command.businessKey())) {
            throw new BusinessException("CONFLICT", "业务单据已存在流程实例");
        }

        WorkflowInstance instance = new WorkflowInstance();
        instance.setTenantId(tenantId);
        instance.setDefinitionId(definition.getId());
        instance.setDefinitionKey(definition.getDefinitionKey());
        instance.setDefinitionVersion(definition.getVersion());
        instance.setBusinessKey(command.businessKey().trim());
        instance.setTitle(command.title().trim());
        instance.setStatus(WorkflowInstanceStatus.RUNNING);
        instance.setStarterUserId(user.id());
        instance.setStarterUsername(user.username());
        instance.setCurrentStepIndex(0);
        instance.setVariablesSnapshot(codec.snapshotVariables(command.variables()));
        instance.setStartedAt(TimeSupport.now());
        try {
            repository.insertInstance(instance);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("CONFLICT", "业务单据已存在流程实例");
        }

        WorkflowTask task = store.createPendingTask(tenantId, instance, definition, steps.get(0), 0);
        notifier.publishWorkflowTodoCreated(tenantId, instance, task, user.username());
        return new WorkflowStartResult(viewMapper.toInstanceView(instance), viewMapper.toTaskView(task, user));
    }

    public WorkflowInstanceView instance(Long instanceId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WorkflowInstance instance = store.requireInstance(tenantId, instanceId);
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
        long total = repository.countStartedInstances(tenantId, user.id(), status);
        int offset = (int) Math.min(Integer.MAX_VALUE, PaginationSupport.offset(normalizedPage, normalizedSize));
        List<WorkflowInstanceView> records = repository
                .findStartedInstances(tenantId, user.id(), status, offset, normalizedSize)
                .stream().map(viewMapper::toInstanceView).toList();
        return PageResult.of(total, normalizedPage, normalizedSize, records);
    }

    @Transactional
    public WorkflowInstanceView withdrawInstance(Long instanceId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WorkflowInstance instance = store.requireRunningInstance(tenantId, instanceId);
        if (!Objects.equals(instance.getStarterUserId(), user.id())) {
            throw new BusinessException("ACCESS_DENIED", "只有发起人可以撤回流程");
        }
        WorkflowRecipients recipients = store.pendingTaskRecipients(tenantId, instance.getId());
        store.cancelPendingTasks(tenantId, instance.getId(), WorkflowTaskStatus.CANCELLED, "发起人撤回");
        instance.setStatus(WorkflowInstanceStatus.WITHDRAWN);
        instance.setEndedAt(TimeSupport.now());
        store.updateInstance(instance);
        notifier.publishWorkflowInstanceClosed(tenantId, instance, recipients, user.id(), user.username(), true);
        return viewMapper.toInstanceView(instance);
    }

    @Transactional
    public WorkflowInstanceView terminateInstance(Long instanceId, WorkflowTaskCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WorkflowInstance instance = store.requireRunningInstance(tenantId, instanceId);
        WorkflowRecipients recipients = store.pendingTaskRecipients(tenantId, instance.getId());
        store.cancelPendingTasks(
                tenantId, instance.getId(), WorkflowTaskStatus.CANCELLED,
                WorkflowSupport.normalizeText(command.comment()));
        instance.setStatus(WorkflowInstanceStatus.TERMINATED);
        instance.setEndedAt(TimeSupport.now());
        store.updateInstance(instance);
        notifier.publishWorkflowInstanceClosed(tenantId, instance, recipients, user.id(), user.username(), false);
        return viewMapper.toInstanceView(instance);
    }
}
