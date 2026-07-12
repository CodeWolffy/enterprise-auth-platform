package com.enterprise.auth.platform.modules.workflow.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.web.PaginationSupport;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade.EnabledUser;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowDefinition;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstance;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstanceStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRejectResolver;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRepository;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowStepDefinition;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTask;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowTaskService {

    private static final int TODO_CANDIDATE_LIMIT = 500;

    private final WorkflowRepository repository;
    private final WorkflowStore store;
    private final WorkflowViewMapper viewMapper;
    private final WorkflowNotifier notifier;
    private final CurrentUserService currentUserService;

    public WorkflowTaskService(
            WorkflowRepository repository,
            WorkflowStore store,
            WorkflowViewMapper viewMapper,
            WorkflowNotifier notifier,
            CurrentUserService currentUserService
    ) {
        this.repository = repository;
        this.store = store;
        this.viewMapper = viewMapper;
        this.notifier = notifier;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public WorkflowActionResult approveTask(Long taskId, WorkflowTaskCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WorkflowTask task = store.requirePendingTask(tenantId, taskId);
        WorkflowInstance instance = store.requireRunningInstance(tenantId, task.getInstanceId());
        store.ensureActionable(task, user);
        WorkflowDefinition definition = store.requireDefinition(tenantId, task.getDefinitionId());
        List<WorkflowStepDefinition> steps = definition.getSteps();

        completeTask(task, user, command.comment(), WorkflowTaskStatus.APPROVED, TimeSupport.now());

        WorkflowTask nextTask = null;
        int nextStepIndex = task.getStepIndex() < 0 ? 0 : task.getStepIndex() + 1;
        if (nextStepIndex >= steps.size()) {
            instance.setStatus(WorkflowInstanceStatus.APPROVED);
            instance.setEndedAt(TimeSupport.now());
        } else {
            instance.setCurrentStepIndex(nextStepIndex);
            nextTask = store.createPendingTask(
                    tenantId, instance, definition, steps.get(nextStepIndex), nextStepIndex);
        }
        store.updateInstance(instance);
        notifier.publishWorkflowTaskDecision(tenantId, instance, task, user.username(), true);
        if (nextTask != null) {
            notifier.publishWorkflowTodoCreated(tenantId, instance, nextTask, user.username());
        }
        return new WorkflowActionResult(
                viewMapper.toInstanceView(instance), nextTask == null ? null : viewMapper.toTaskView(nextTask, user));
    }

    @Transactional
    public WorkflowActionResult rejectTask(Long taskId, WorkflowTaskCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WorkflowTask task = store.requirePendingTask(tenantId, taskId);
        WorkflowInstance instance = store.requireRunningInstance(tenantId, task.getInstanceId());
        store.ensureActionable(task, user);
        WorkflowDefinition definition = store.requireDefinition(tenantId, task.getDefinitionId());
        List<WorkflowStepDefinition> steps = definition.getSteps();
        WorkflowStepDefinition currentStep = WorkflowSupport.stepAt(steps, task.getStepIndex());
        int nextStepIndex = WorkflowRejectResolver.resolveTarget(
                currentStep.rejectStrategy(), currentStep.rejectTarget(), task.getStepIndex(), steps.size());

        completeTask(task, user, command.comment(), WorkflowTaskStatus.REJECTED, TimeSupport.now());

        WorkflowTask nextTask = null;
        if (nextStepIndex == WorkflowRejectResolver.TARGET_END) {
            instance.setStatus(WorkflowInstanceStatus.REJECTED);
            instance.setEndedAt(TimeSupport.now());
        } else if (nextStepIndex == WorkflowRejectResolver.TARGET_STARTER) {
            instance.setCurrentStepIndex(-1);
            nextTask = store.createStarterReworkTask(tenantId, instance, definition, currentStep);
        } else {
            instance.setCurrentStepIndex(nextStepIndex);
            nextTask = store.createPendingTask(
                    tenantId, instance, definition, steps.get(nextStepIndex), nextStepIndex);
        }
        store.updateInstance(instance);
        notifier.publishWorkflowTaskDecision(tenantId, instance, task, user.username(), false);
        if (nextTask != null) {
            notifier.publishWorkflowTodoCreated(tenantId, instance, nextTask, user.username());
        }
        return new WorkflowActionResult(
                viewMapper.toInstanceView(instance), nextTask == null ? null : viewMapper.toTaskView(nextTask, user));
    }

    @Transactional
    public WorkflowActionResult transferTask(Long taskId, WorkflowTaskTransferCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        WorkflowTask task = store.requirePendingTask(tenantId, taskId);
        WorkflowInstance instance = store.requireRunningInstance(tenantId, task.getInstanceId());
        store.ensureActionable(task, user);
        EnabledUser targetUser = store.requireEnabledUser(tenantId, command.targetUserId());
        if (Objects.equals(targetUser.id(), user.id())) {
            throw new BusinessException("不能转签给自己");
        }

        Instant now = TimeSupport.now();
        completeTask(task, user, command.comment(), WorkflowTaskStatus.TRANSFERRED, now);

        WorkflowTask transferredTask = new WorkflowTask();
        transferredTask.setTenantId(tenantId);
        transferredTask.setInstanceId(task.getInstanceId());
        transferredTask.setDefinitionId(task.getDefinitionId());
        transferredTask.setStepIndex(task.getStepIndex());
        transferredTask.setStepName(task.getStepName());
        transferredTask.setStatus(WorkflowTaskStatus.PENDING);
        transferredTask.setCandidateUserIds(Set.of(targetUser.id()));
        transferredTask.setCandidateGroupCodes(Set.of());
        transferredTask.setAssigneeUserId(targetUser.id());
        transferredTask.setAssigneeUsername(targetUser.username());
        transferredTask.setComment("由 " + user.username() + " 转签");
        store.insertTask(transferredTask);

        notifier.publishWorkflowTaskTransferred(
                tenantId, instance, task, transferredTask, targetUser, user.username());
        notifier.publishWorkflowTodoCreated(tenantId, instance, transferredTask, user.username());
        return new WorkflowActionResult(
                viewMapper.toInstanceView(instance), viewMapper.toTaskView(transferredTask, user));
    }

    public PageResult<WorkflowTaskView> todoTasks(int page, int size) {
        return todoTasks(page, size, null);
    }

    public PageResult<WorkflowTaskView> todoTasks(int page, int size, Long taskId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        List<WorkflowTask> candidates = repository
                .findTodoCandidates(tenantId, user.id(), taskId, TODO_CANDIDATE_LIMIT)
                .stream()
                .filter(task -> store.isActionable(task, user))
                .toList();
        // 先内存分页，再对当前页批量查催办，避免候选集 500 次 COUNT
        PageResult<WorkflowTask> pageResult = WorkflowSupport.page(candidates, page, size);
        Map<Long, Long> urgeCounts = repository.countUrgesByTaskIds(
                tenantId,
                pageResult.records().stream().map(WorkflowTask::getId).toList()
        );
        List<WorkflowTaskView> views = viewMapper.toTaskViews(pageResult.records(), user, urgeCounts);
        return PageResult.of(pageResult.total(), pageResult.page(), pageResult.size(), views);
    }

    public PageResult<WorkflowTaskView> doneTasks(int page, int size) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = WorkflowSupport.currentTenantId(user);
        int normalizedPage = PaginationSupport.normalizePage(page);
        int normalizedSize = WorkflowSupport.normalizeSize(size);
        long total = repository.countDoneTasks(tenantId, user.id());
        int offset = (int) Math.min(Integer.MAX_VALUE, PaginationSupport.offset(normalizedPage, normalizedSize));
        List<WorkflowTask> tasks = repository.findDoneTasks(tenantId, user.id(), offset, normalizedSize);
        Map<Long, Long> urgeCounts = repository.countUrgesByTaskIds(
                tenantId,
                tasks.stream().map(WorkflowTask::getId).toList()
        );
        List<WorkflowTaskView> records = viewMapper.toTaskViews(tasks, user, urgeCounts);
        return PageResult.of(total, normalizedPage, normalizedSize, records);
    }

    private void completeTask(
            WorkflowTask task,
            UserAccount user,
            String comment,
            WorkflowTaskStatus status,
            Instant completedAt
    ) {
        task.setStatus(status);
        task.setAssigneeUserId(user.id());
        task.setAssigneeUsername(user.username());
        task.setComment(WorkflowSupport.normalizeText(comment));
        task.setCompletedAt(completedAt);
        store.completePendingTask(task);
    }
}
