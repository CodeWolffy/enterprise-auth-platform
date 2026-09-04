package com.enterprise.auth.platform.modules.workflow.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.common.context.TimeZoneContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.web.PaginationSupport;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.notification.application.NotificationPublishCommand;
import com.enterprise.auth.platform.modules.notification.application.NotificationPublisher;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstance;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRepository;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTask;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskUrge;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class WorkflowTaskUrgeService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTaskUrgeService.class);
    private static final int SAME_TASK_COOLDOWN_MINUTES = 1;
    private static final int DAILY_TASK_LIMIT = 5;

    private final WorkflowRepository repository;
    private final WorkflowStore store;
    private final CurrentUserService currentUserService;
    private final NotificationPublisher notificationPublisher;

    public WorkflowTaskUrgeService(
            WorkflowRepository repository,
            WorkflowStore store,
            CurrentUserService currentUserService,
            NotificationPublisher notificationPublisher
    ) {
        this.repository = repository;
        this.store = store;
        this.currentUserService = currentUserService;
        this.notificationPublisher = notificationPublisher;
    }

    @Transactional
    public WorkflowTaskUrgeResult urge(Long taskId, WorkflowTaskCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform(user.tenantId());
        WorkflowTask task = repository.findPendingTask(tenantId, taskId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "待办任务不存在或已结束"));
        if (!canUrge(task, user)) {
            throw new BusinessException("ACCESS_DENIED", "无权催办该任务");
        }
        enforceUrgeFrequency(tenantId, task.getId(), user.id());

        WorkflowTaskUrge urge = new WorkflowTaskUrge();
        urge.setTenantId(tenantId);
        urge.setTaskId(task.getId());
        urge.setInstanceId(task.getInstanceId());
        urge.setUrgedByUserId(user.id());
        urge.setUrgedByUsername(user.username());
        urge.setComment(WorkflowSupport.normalizeText(command.comment()));
        urge.setUrgedAt(TimeSupport.now());
        repository.insertUrge(urge);

        Set<String> targets = urgeTargets(task);
        publishAfterCommit(buildUrgeNotification(tenantId, task, urge, user));
        WorkflowTaskUrgeView view = WorkflowTaskUrgeView.from(urge, targets);
        return new WorkflowTaskUrgeResult(view, countUrges(task.getTenantId(), task.getId()), null);
    }

    public List<WorkflowTaskUrgeView> listUrges(Long taskId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform(user.tenantId());
        WorkflowTask task = store.requireTask(tenantId, taskId);
        if (!canViewUrges(task, user)) {
            throw new BusinessException("ACCESS_DENIED", "无权查看该任务催办历史");
        }
        Set<String> targets = urgeTargets(task);
        return repository.findUrgesByTask(tenantId, taskId).stream()
                .map(urge -> WorkflowTaskUrgeView.from(urge, targets))
                .toList();
    }

    public int countUrges(String tenantId, Long taskId) {
        return Math.toIntExact(repository.countUrges(tenantId, taskId));
    }

    public PageResult<WorkflowTaskUrgeView> listUrgesByInstance(Long instanceId, int page, int size) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform(user.tenantId());
        ensureInstanceUrgesVisible(tenantId, instanceId, user);
        int safePage = PaginationSupport.normalizePage(page);
        int safeSize = PaginationSupport.normalizeSize(size, 100);
        long total = repository.countUrgesByInstance(tenantId, instanceId);
        int offset = (int) Math.min(
                Integer.MAX_VALUE,
                PaginationSupport.offset(safePage, safeSize));
        List<WorkflowTaskUrgeView> records = repository
                .findUrgesByInstance(tenantId, instanceId, offset, safeSize)
                .stream()
                .map(urge -> WorkflowTaskUrgeView.from(urge, Set.of("当前处理人")))
                .toList();
        return PageResult.of(total, safePage, safeSize, records);
    }

    private boolean canUrge(WorkflowTask task, UserAccount user) {
        if (user.permissions().contains(PermissionCodes.WORKFLOW_TODO_EDIT)) {
            return true;
        }
        return repository.findInstance(task.getTenantId(), task.getInstanceId())
                .map(instance -> Objects.equals(instance.getStarterUserId(), user.id()))
                .orElse(false);
    }

    private void enforceUrgeFrequency(String tenantId, Long taskId, Long userId) {
        Instant now = TimeSupport.now();
        if (repository.countUserUrgesSince(
                tenantId, taskId, userId, now.minus(Duration.ofMinutes(SAME_TASK_COOLDOWN_MINUTES))) > 0) {
            throw new BusinessException("RATE_LIMITED", "催办过于频繁，请稍后再试");
        }
        Instant todayStart = TimeSupport.startOfDay(
                TimeSupport.today(TimeZoneContext.getZone()), TimeZoneContext.getZone());
        if (repository.countUserUrgesSince(tenantId, taskId, userId, todayStart) >= DAILY_TASK_LIMIT) {
            throw new BusinessException("RATE_LIMITED", "该任务今日催办次数已达上限");
        }
    }

    private boolean canViewUrges(WorkflowTask task, UserAccount user) {
        if (user.permissions().contains(PermissionCodes.WORKFLOW_TODO_EDIT)
                || user.permissions().contains(PermissionCodes.WORKFLOW_TODO_GET)) {
            return true;
        }
        if (repository.findInstance(task.getTenantId(), task.getInstanceId())
                .map(instance -> Objects.equals(instance.getStarterUserId(), user.id()))
                .orElse(false)) {
            return true;
        }
        if (Objects.equals(task.getAssigneeUserId(), user.id()) || task.getCandidateUserIds().contains(user.id())) {
            return true;
        }
        return user.roles().stream().anyMatch(task.getCandidateGroupCodes()::contains);
    }

    private void ensureInstanceUrgesVisible(String tenantId, Long instanceId, UserAccount user) {
        List<WorkflowTask> tasks = repository.findInstanceTasks(tenantId, instanceId);
        if (tasks.isEmpty()) {
            throw new BusinessException("NOT_FOUND", "流程实例催办记录不存在");
        }
        if (user.permissions().contains(PermissionCodes.WORKFLOW_TODO_EDIT)
                || user.permissions().contains(PermissionCodes.WORKFLOW_TODO_GET)) {
            return;
        }
        if (repository.findInstance(tenantId, instanceId)
                .map(instance -> Objects.equals(instance.getStarterUserId(), user.id()))
                .orElse(false)) {
            return;
        }
        if (tasks.stream().noneMatch(task -> canViewUrges(task, user))) {
            throw new BusinessException("ACCESS_DENIED", "无权查看该流程实例催办记录");
        }
    }

    private Set<String> urgeTargets(WorkflowTask task) {
        Set<String> targets = new LinkedHashSet<>();
        if (StringUtils.hasText(task.getAssigneeUsername())) {
            targets.add(task.getAssigneeUsername().trim());
        }
        if (!task.getCandidateUserIds().isEmpty()) {
            targets.add("候选人 " + task.getCandidateUserIds().size() + " 人");
        }
        task.getCandidateGroupCodes().forEach(groupCode -> targets.add("候选组 " + groupCode));
        if (targets.isEmpty()) {
            targets.add("当前处理人");
        }
        return targets;
    }

    private NotificationPublishCommand buildUrgeNotification(
            String tenantId, WorkflowTask task, WorkflowTaskUrge urge, UserAccount sender) {
        Set<Long> recipientUserIds = new LinkedHashSet<>();
        Set<String> recipientRoleCodes = new LinkedHashSet<>();
        if (task.getAssigneeUserId() != null) {
            recipientUserIds.add(task.getAssigneeUserId());
        } else {
            recipientUserIds.addAll(task.getCandidateUserIds());
            recipientRoleCodes.addAll(task.getCandidateGroupCodes());
        }
        if (recipientUserIds.isEmpty() && recipientRoleCodes.isEmpty()) {
            return null;
        }
        WorkflowInstance instance = repository.findInstance(tenantId, task.getInstanceId()).orElse(null);
        String instanceTitle = instance == null || !StringUtils.hasText(instance.getTitle())
                ? "流程实例" : instance.getTitle().trim();
        String businessKey = instance == null || !StringUtils.hasText(instance.getBusinessKey())
                ? String.valueOf(task.getInstanceId()) : instance.getBusinessKey().trim();
        String reason = StringUtils.hasText(urge.getComment()) ? urge.getComment().trim() : "无";
        String stepName = StringUtils.hasText(task.getStepName()) ? task.getStepName().trim() : "当前节点";
        return new NotificationPublishCommand(
                tenantId,
                "WORKFLOW_TASK_URGE",
                "WORKFLOW_TASK",
                String.valueOf(task.getId()),
                "WORKFLOW_TASK",
                String.valueOf(task.getId()),
                recipientUserIds,
                recipientRoleCodes,
                false,
                Map.of(
                        "taskId", task.getId(),
                        "urgeId", urge.getId(),
                        "instanceId", task.getInstanceId(),
                        "instanceTitle", instanceTitle,
                        "businessKey", businessKey,
                        "stepName", stepName,
                        "senderName", sender.username(),
                        "reason", reason
                ),
                "流程待办催办：" + instanceTitle,
                "流程编号：" + businessKey
                        + "\n催办人：" + sender.username()
                        + "\n当前节点：" + stepName
                        + "\n催办原因：" + reason,
                "WARNING",
                "/workflow/todo?taskId=" + task.getId(),
                Map.of("route", "/workflow/todo", "taskId", task.getId()),
                Map.of("instanceId", task.getInstanceId(), "urgeId", urge.getId()),
                "WORKFLOW_TASK_URGE:" + urge.getId(),
                null,
                sender.username()
        );
    }

    private void publishAfterCommit(NotificationPublishCommand command) {
        if (command == null) {
            return;
        }
        Runnable publishTask = () -> {
            try {
                notificationPublisher.publish(command);
            } catch (Exception exception) {
                log.warn("流程催办通知发布失败。tenantId={}，bizId={}，dedupKey={}",
                        command.tenantId(), command.bizId(), command.dedupKey(), exception);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishTask.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishTask.run();
            }
        });
    }
}
