package com.enterprise.auth.platform.modules.workflow.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.audit.AuditEventPublisher;
import com.enterprise.auth.platform.common.audit.PlatformAuditEvent;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.notification.application.NotificationPublishCommand;
import com.enterprise.auth.platform.modules.notification.application.NotificationPublisher;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskStatus;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessInstanceEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskUrgeEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfProcessInstanceMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.mapper.WfTaskUrgeMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    private final WfTaskUrgeMapper urgeMapper;
    private final WfTaskMapper taskMapper;
    private final WfProcessInstanceMapper instanceMapper;
    private final CurrentUserService currentUserService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;
    private final NotificationPublisher notificationPublisher;
    private static final TypeReference<java.util.Set<Long>> LONG_SET_TYPE = new TypeReference<>() { };
    private static final TypeReference<java.util.Set<String>> STRING_SET_TYPE = new TypeReference<>() { };

    public WorkflowTaskUrgeService(
            WfTaskUrgeMapper urgeMapper,
            WfTaskMapper taskMapper,
            WfProcessInstanceMapper instanceMapper,
            CurrentUserService currentUserService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper,
            NotificationPublisher notificationPublisher
    ) {
        this.urgeMapper = urgeMapper;
        this.taskMapper = taskMapper;
        this.instanceMapper = instanceMapper;
        this.currentUserService = currentUserService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
        this.notificationPublisher = notificationPublisher;
    }

    @Transactional
    public WorkflowTaskUrgeResult urge(Long taskId, WorkflowTaskCommand command) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        WfTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getId, taskId)
                .eq(WfTaskEntity::getTenantId, tenantId)
                .eq(WfTaskEntity::getStatus, WorkflowTaskStatus.PENDING.name())
                .eq(WfTaskEntity::getDeleted, 0)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException("NOT_FOUND", "待办任务不存在或已结束");
        }
        if (!canUrge(task, user)) {
            throw new BusinessException("ACCESS_DENIED", "无权催办该任务");
        }
        enforceUrgeFrequency(tenantId, task.getId(), user.id());

        WfTaskUrgeEntity entity = new WfTaskUrgeEntity();
        entity.setTenantId(tenantId);
        entity.setTaskId(task.getId());
        entity.setInstanceId(task.getInstanceId());
        entity.setUrgedByUserId(user.id());
        entity.setUrgedByUsername(user.username());
        entity.setComment(StringUtils.hasText(command.comment()) ? command.comment().trim() : null);
        entity.setUrgedAt(TimeSupport.utcNowDateTime());
        urgeMapper.insert(entity);

        Set<String> targets = urgeTargets(task);
        publishAfterCommit(buildUrgeNotification(tenantId, task, entity, user));
        WorkflowTaskUrgeView view = WorkflowTaskUrgeView.from(entity, targets);
        int total = countUrges(task.getTenantId(), task.getId());
        auditEventPublisher.publish(PlatformAuditEvent.of("WORKFLOW_TASK_URGED", user.username(), tenantId, Map.of(
                "taskId", task.getId(),
                "instanceId", task.getInstanceId(),
                "urgeId", entity.getId(),
                "totalUrgeCount", total
        )));
        return new WorkflowTaskUrgeResult(view, total, null);
    }

    public List<WorkflowTaskUrgeView> listUrges(Long taskId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        WfTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getId, taskId)
                .eq(WfTaskEntity::getTenantId, tenantId)
                .eq(WfTaskEntity::getDeleted, 0)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException("NOT_FOUND", "任务不存在");
        }
        if (!canViewUrges(task, user)) {
            throw new BusinessException("ACCESS_DENIED", "无权查看该任务催办历史");
        }
        List<WfTaskUrgeEntity> records = urgeMapper.selectList(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                .eq(WfTaskUrgeEntity::getTenantId, tenantId)
                .eq(WfTaskUrgeEntity::getTaskId, taskId)
                .eq(WfTaskUrgeEntity::getDeleted, 0)
                .orderByDesc(WfTaskUrgeEntity::getUrgedAt)
                .orderByDesc(WfTaskUrgeEntity::getId));
        List<WorkflowTaskUrgeView> views = new ArrayList<>();
        for (WfTaskUrgeEntity entity : records) {
            views.add(WorkflowTaskUrgeView.from(entity, urgeTargets(task)));
        }
        return views;
    }

    public int countUrges(String tenantId, Long taskId) {
        Long count = urgeMapper.selectCount(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                .eq(WfTaskUrgeEntity::getTenantId, tenantId)
                .eq(WfTaskUrgeEntity::getTaskId, taskId)
                .eq(WfTaskUrgeEntity::getDeleted, 0));
        return count == null ? 0 : count.intValue();
    }

    public PageResult<WorkflowTaskUrgeView> listUrgesByInstance(Long instanceId, int page, int size) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        ensureInstanceUrgesVisible(tenantId, instanceId, user);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long total = urgeMapper.selectCount(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                .eq(WfTaskUrgeEntity::getTenantId, tenantId)
                .eq(WfTaskUrgeEntity::getInstanceId, instanceId)
                .eq(WfTaskUrgeEntity::getDeleted, 0));
        int offset = (safePage - 1) * safeSize;
        List<WfTaskUrgeEntity> records = urgeMapper.selectList(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                .eq(WfTaskUrgeEntity::getTenantId, tenantId)
                .eq(WfTaskUrgeEntity::getInstanceId, instanceId)
                .eq(WfTaskUrgeEntity::getDeleted, 0)
                .orderByDesc(WfTaskUrgeEntity::getUrgedAt)
                .orderByDesc(WfTaskUrgeEntity::getId)
                .last("limit " + offset + "," + safeSize));
        List<WorkflowTaskUrgeView> views = new ArrayList<>();
        for (WfTaskUrgeEntity entity : records) {
            views.add(WorkflowTaskUrgeView.from(entity, Set.of("当前处理人")));
        }
        return PageResult.of(total, safePage, safeSize, views);
    }

    private boolean canUrge(WfTaskEntity task, UserAccount user) {
        if (user.permissions().contains(PermissionCodes.WORKFLOW_TODO_EDIT)) {
            return true;
        }
        WfProcessInstanceEntity instance = instanceMapper.selectOne(new LambdaQueryWrapper<WfProcessInstanceEntity>()
                .eq(WfProcessInstanceEntity::getTenantId, task.getTenantId())
                .eq(WfProcessInstanceEntity::getId, task.getInstanceId())
                .eq(WfProcessInstanceEntity::getDeleted, 0)
                .last("limit 1"));
        return instance != null && Objects.equals(instance.getStarterUserId(), user.id());
    }

    private void enforceUrgeFrequency(String tenantId, Long taskId, Long userId) {
        LocalDateTime now = TimeSupport.utcNowDateTime();
        Long recentCount = urgeMapper.selectCount(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                .eq(WfTaskUrgeEntity::getTenantId, tenantId)
                .eq(WfTaskUrgeEntity::getTaskId, taskId)
                .eq(WfTaskUrgeEntity::getUrgedByUserId, userId)
                .eq(WfTaskUrgeEntity::getDeleted, 0)
                .ge(WfTaskUrgeEntity::getUrgedAt, now.minusMinutes(SAME_TASK_COOLDOWN_MINUTES)));
        if (recentCount != null && recentCount > 0) {
            throw new BusinessException("RATE_LIMITED", "催办过于频繁，请稍后再试");
        }
        Long dailyCount = urgeMapper.selectCount(new LambdaQueryWrapper<WfTaskUrgeEntity>()
                .eq(WfTaskUrgeEntity::getTenantId, tenantId)
                .eq(WfTaskUrgeEntity::getTaskId, taskId)
                .eq(WfTaskUrgeEntity::getUrgedByUserId, userId)
                .eq(WfTaskUrgeEntity::getDeleted, 0)
                .ge(WfTaskUrgeEntity::getUrgedAt, now.minusDays(1)));
        if (dailyCount != null && dailyCount >= DAILY_TASK_LIMIT) {
            throw new BusinessException("RATE_LIMITED", "该任务今日催办次数已达上限");
        }
    }

    private boolean canViewUrges(WfTaskEntity task, UserAccount user) {
        if (user.permissions().contains(PermissionCodes.WORKFLOW_TODO_EDIT) || user.permissions().contains(PermissionCodes.WORKFLOW_TODO_GET)) {
            return true;
        }
        WfProcessInstanceEntity instance = instanceMapper.selectOne(new LambdaQueryWrapper<WfProcessInstanceEntity>()
                .eq(WfProcessInstanceEntity::getTenantId, task.getTenantId())
                .eq(WfProcessInstanceEntity::getId, task.getInstanceId())
                .eq(WfProcessInstanceEntity::getDeleted, 0)
                .last("limit 1"));
        if (instance != null && Objects.equals(instance.getStarterUserId(), user.id())) {
            return true;
        }
        if (task.getAssigneeUserId() != null && Objects.equals(task.getAssigneeUserId(), user.id())) {
            return true;
        }
        Set<Long> candidateUserIds = parseLongSet(task.getCandidateUserIdsJson());
        if (candidateUserIds.contains(user.id())) {
            return true;
        }
        Set<String> candidateGroupCodes = parseStringSet(task.getCandidateGroupCodesJson());
        return user.roles().stream().anyMatch(candidateGroupCodes::contains);
    }

    private void ensureInstanceUrgesVisible(String tenantId, Long instanceId, UserAccount user) {
        Long taskCount = taskMapper.selectCount(new LambdaQueryWrapper<WfTaskEntity>()
                .eq(WfTaskEntity::getTenantId, tenantId)
                .eq(WfTaskEntity::getInstanceId, instanceId)
                .eq(WfTaskEntity::getDeleted, 0));
        if (taskCount == null || taskCount == 0) {
            throw new BusinessException("NOT_FOUND", "流程实例催办记录不存在");
        }
        if (user.permissions().contains(PermissionCodes.WORKFLOW_TODO_EDIT) || user.permissions().contains(PermissionCodes.WORKFLOW_TODO_GET)) {
            return;
        }
        WfProcessInstanceEntity instance = instanceMapper.selectOne(new LambdaQueryWrapper<WfProcessInstanceEntity>()
                .eq(WfProcessInstanceEntity::getTenantId, tenantId)
                .eq(WfProcessInstanceEntity::getId, instanceId)
                .eq(WfProcessInstanceEntity::getDeleted, 0)
                .last("limit 1"));
        if (instance != null && Objects.equals(instance.getStarterUserId(), user.id())) {
            return;
        }
        boolean visible = taskMapper.selectList(new LambdaQueryWrapper<WfTaskEntity>()
                        .eq(WfTaskEntity::getTenantId, tenantId)
                        .eq(WfTaskEntity::getInstanceId, instanceId)
                        .eq(WfTaskEntity::getDeleted, 0))
                .stream()
                .anyMatch(task -> canViewUrges(task, user));
        if (!visible) {
            throw new BusinessException("ACCESS_DENIED", "无权查看该流程实例催办记录");
        }
    }

    private Set<String> urgeTargets(WfTaskEntity task) {
        Set<String> targets = new LinkedHashSet<>();
        if (StringUtils.hasText(task.getAssigneeUsername())) {
            targets.add(task.getAssigneeUsername().trim());
        }
        Set<Long> candidateUserIds = parseLongSet(task.getCandidateUserIdsJson());
        if (!candidateUserIds.isEmpty()) {
            targets.add("候选人 " + candidateUserIds.size() + " 人");
        }
        Set<String> candidateGroupCodes = parseStringSet(task.getCandidateGroupCodesJson());
        for (String groupCode : candidateGroupCodes) {
            targets.add("候选组 " + groupCode);
        }
        if (targets.isEmpty()) {
            targets.add("当前处理人");
        }
        return targets;
    }

    private NotificationPublishCommand buildUrgeNotification(String tenantId, WfTaskEntity task, WfTaskUrgeEntity urge, UserAccount sender) {
        Set<Long> recipientUserIds = new LinkedHashSet<>();
        Set<String> recipientRoleCodes = new LinkedHashSet<>();
        if (task.getAssigneeUserId() != null) {
            recipientUserIds.add(task.getAssigneeUserId());
        } else {
            recipientUserIds.addAll(parseLongSet(task.getCandidateUserIdsJson()));
            recipientRoleCodes.addAll(parseStringSet(task.getCandidateGroupCodesJson()));
        }
        if (recipientUserIds.isEmpty() && recipientRoleCodes.isEmpty()) {
            return null;
        }
        WfProcessInstanceEntity instance = instanceMapper.selectOne(new LambdaQueryWrapper<WfProcessInstanceEntity>()
                .eq(WfProcessInstanceEntity::getTenantId, tenantId)
                .eq(WfProcessInstanceEntity::getId, task.getInstanceId())
                .eq(WfProcessInstanceEntity::getDeleted, 0)
                .last("limit 1"));
        String instanceTitle = instance == null || !StringUtils.hasText(instance.getTitle()) ? "流程实例" : instance.getTitle().trim();
        String businessKey = instance == null || !StringUtils.hasText(instance.getBusinessKey()) ? String.valueOf(task.getInstanceId()) : instance.getBusinessKey().trim();
        String reason = StringUtils.hasText(urge.getComment()) ? urge.getComment().trim() : "无";
        String stepName = StringUtils.hasText(task.getStepName()) ? task.getStepName().trim() : "当前节点";
        String title = "流程待办催办：" + instanceTitle;
        String content = "流程编号：" + businessKey
                + "\n催办人：" + sender.username()
                + "\n当前节点：" + stepName
                + "\n催办原因：" + reason;
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
                title,
                content,
                "WARNING",
                "/platform/workflow/todo?taskId=" + task.getId(),
                Map.of("route", "/platform/workflow/todo", "taskId", task.getId()),
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
            } catch (Exception ex) {
                log.warn("流程催办通知发布失败。tenantId={}，bizId={}，dedupKey={}",
                        command.tenantId(), command.bizId(), command.dedupKey(), ex);
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

    private Set<Long> parseLongSet(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            Set<Long> values = objectMapper.readValue(json, LONG_SET_TYPE);
            return values == null ? Set.of() : values;
        } catch (JsonProcessingException ex) {
            log.debug("流程候选用户 ID 解析失败，返回空集合。error={}", ex.getMessage());
            return Set.of();
        }
    }

    private Set<String> parseStringSet(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            Set<String> values = objectMapper.readValue(json, STRING_SET_TYPE);
            return values == null ? Set.of() : values;
        } catch (JsonProcessingException ex) {
            log.debug("流程候选组编码解析失败，返回空集合。error={}", ex.getMessage());
            return Set.of();
        }
    }

    private String currentTenantId(UserAccount user) {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : user.tenantId();
    }
}