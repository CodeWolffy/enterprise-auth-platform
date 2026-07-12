package com.enterprise.auth.platform.modules.notification.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.context.TimeZoneContext;
import com.enterprise.auth.platform.common.notification.NotificationScenarioPort;
import com.enterprise.auth.platform.modules.system.application.OutboxDispatchWorker;
import com.enterprise.auth.platform.modules.system.application.OutboxWriter;
import com.enterprise.auth.platform.modules.user.application.UserAuthenticationFacade;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class NotificationScenarioPublisher implements NotificationScenarioPort {

    private static final String WORKFLOW_TODO_LINK = "/workflow/todo";
    private static final String WORKFLOW_INSTANCE_LINK = "/workflow/instances";
    private static final String ACCOUNT_PROFILE_LINK = "/account/profile";

    private final NotificationSseRegistry sseRegistry;
    private final UserAuthenticationFacade userAuthenticationFacade;
    private final OutboxWriter outboxWriter;
    private final OutboxDispatchWorker outboxDispatchWorker;

    public NotificationScenarioPublisher(
            NotificationSseRegistry sseRegistry,
            UserAuthenticationFacade userAuthenticationFacade,
            OutboxWriter outboxWriter,
            OutboxDispatchWorker outboxDispatchWorker
    ) {
        this.sseRegistry = sseRegistry;
        this.userAuthenticationFacade = userAuthenticationFacade;
        this.outboxWriter = outboxWriter;
        this.outboxDispatchWorker = outboxDispatchWorker;
    }

    @Override
    public void workflowTodoCreated(WorkflowTodoCreatedEvent event) {
        if (event == null || event.emptyRecipients()) {
            return;
        }
        String title = "新的流程待办：" + fallback(event.instanceTitle(), "流程实例");
        String content = "流程编号：" + fallback(event.businessKey(), String.valueOf(event.instanceId()))
                + "\n当前节点：" + fallback(event.stepName(), "待处理")
                + "\n请及时处理该流程待办";
        publishAfterCommit(new NotificationPublishCommand(
                event.tenantId(),
                "WORKFLOW_TODO_CREATED",
                "WORKFLOW_TASK",
                String.valueOf(event.taskId()),
                "WORKFLOW_TASK",
                String.valueOf(event.taskId()),
                event.recipientUserIds(),
                event.recipientRoleCodes(),
                false,
                Map.of(
                        "instanceId", event.instanceId(),
                        "taskId", event.taskId(),
                        "businessKey", fallback(event.businessKey(), ""),
                        "stepName", fallback(event.stepName(), "")
                ),
                title,
                content,
                "INFO",
                WORKFLOW_TODO_LINK + "?taskId=" + event.taskId(),
                Map.of("route", WORKFLOW_TODO_LINK, "taskId", event.taskId()),
                Map.of("instanceId", event.instanceId(), "taskId", event.taskId()),
                "WORKFLOW_TODO_CREATED:" + event.taskId(),
                null,
                event.operator()
        ));
    }

    @Override
    public void workflowTaskApproved(WorkflowTaskDecisionEvent event) {
        workflowTaskDecision(event, "WORKFLOW_TASK_APPROVED", "流程审批通过：", "审批人", "SUCCESS");
    }

    @Override
    public void workflowTaskRejected(WorkflowTaskDecisionEvent event) {
        workflowTaskDecision(event, "WORKFLOW_TASK_REJECTED", "流程审批驳回：", "驳回人", "ERROR");
    }

    @Override
    public void workflowTaskTransferred(WorkflowTaskTransferEvent event) {
        if (event == null || event.starterUserId() == null) {
            return;
        }
        String title = "流程待办已转签：" + fallback(event.instanceTitle(), "流程实例");
        String content = "流程编号：" + fallback(event.businessKey(), String.valueOf(event.instanceId()))
                + "\n原处理人：" + fallback(event.operator(), "未知")
                + "\n新处理人：" + fallback(event.targetUsername(), String.valueOf(event.targetUserId()))
                + "\n当前节点：" + fallback(event.stepName(), "待处理");
        publishAfterCommit(new NotificationPublishCommand(
                event.tenantId(),
                "WORKFLOW_TASK_TRANSFERRED",
                "WORKFLOW_TASK",
                String.valueOf(event.originalTaskId()),
                "WORKFLOW_INSTANCE",
                String.valueOf(event.instanceId()),
                Set.of(event.starterUserId()),
                Set.of(),
                false,
                Map.of(
                        "instanceId", event.instanceId(),
                        "taskId", event.originalTaskId(),
                        "newTaskId", event.newTaskId(),
                        "targetUserId", event.targetUserId()
                ),
                title,
                content,
                "WARNING",
                WORKFLOW_INSTANCE_LINK,
                Map.of("route", WORKFLOW_INSTANCE_LINK, "instanceId", event.instanceId()),
                Map.of("instanceId", event.instanceId(), "newTaskId", event.newTaskId()),
                "WORKFLOW_TASK_TRANSFERRED:" + event.originalTaskId(),
                null,
                event.operator()
        ));
    }

    @Override
    public void workflowInstanceWithdrawn(WorkflowInstanceClosedEvent event) {
        workflowInstanceClosed(event, "WORKFLOW_INSTANCE_WITHDRAWN", "流程已撤回：", "发起人", "WARNING");
    }

    @Override
    public void workflowInstanceTerminated(WorkflowInstanceClosedEvent event) {
        workflowInstanceClosed(event, "WORKFLOW_INSTANCE_TERMINATED", "流程已终止：", "操作人", "ERROR");
    }

    @Override
    public void accountLocked(String tenantId, String username, String clientIp) {
        SysUserEntity user = loadUserByUsername(tenantId, username);
        if (user == null) {
            return;
        }
        publishAccountSecurity(
                tenantId,
                user.getId(),
                "ACCOUNT_LOCKED",
                "账号已被临时锁定",
                "你的账号因连续登录失败被临时锁定。\n来源 IP：" + fallback(clientIp, "未知"),
                "ERROR",
                "ACCOUNT_LOCKED:" + tenantId + ":" + user.getId() + ":" + TimeSupport.toLocalDate(TimeSupport.now(), TimeZoneContext.getZone()),
                Map.of("clientIp", fallback(clientIp, "")),
                username
        );
    }

    @Override
    public void passwordResetRequested(String tenantId, Long userId, String username, String clientIp) {
        publishAccountSecurity(
                tenantId,
                userId,
                "PASSWORD_RESET_REQUESTED",
                "收到密码重置请求",
                "你的账号收到一次密码重置请求。\n来源 IP：" + fallback(clientIp, "未知") + "\n如果不是你本人操作，请尽快联系管理员。",
                "WARNING",
                "PASSWORD_RESET_REQUESTED:" + userId + ":" + TimeSupport.now(),
                Map.of("clientIp", fallback(clientIp, "")),
                username
        );
    }

    @Override
    public void passwordResetCompleted(String tenantId, Long userId, String username) {
        publishAccountSecurity(
                tenantId,
                userId,
                "PASSWORD_RESET_COMPLETED",
                "密码已重置",
                "你的账号密码已通过重置流程更新，旧会话已失效。",
                "SUCCESS",
                "PASSWORD_RESET_COMPLETED:" + userId + ":" + TimeSupport.now(),
                Map.of(),
                username
        );
    }

    @Override
    public void passwordChanged(String tenantId, Long userId, String username) {
        publishAccountSecurity(
                tenantId,
                userId,
                "PASSWORD_CHANGED",
                "密码已修改",
                "你的账号密码已成功修改。若非本人操作，请立即联系管理员。",
                "SUCCESS",
                "PASSWORD_CHANGED:" + userId + ":" + TimeSupport.now(),
                Map.of(),
                username
        );
    }

    @Override
    public void adminPasswordReset(String tenantId, Long userId, String username, String operator) {
        publishAccountSecurity(
                tenantId,
                userId,
                "ADMIN_PASSWORD_RESET",
                "管理员已重置你的密码",
                "管理员 " + fallback(operator, "系统") + " 已重置你的账号密码，请按要求重新登录并修改密码。",
                "WARNING",
                "ADMIN_PASSWORD_RESET:" + userId + ":" + TimeSupport.now(),
                Map.of("operator", fallback(operator, "")),
                operator
        );
    }

    @Override
    public void accountDisabled(String tenantId, Long userId, String username, String operator) {
        publishAccountSecurity(
                tenantId,
                userId,
                "ACCOUNT_DISABLED",
                "账号已被禁用",
                "管理员 " + fallback(operator, "系统") + " 已禁用你的账号，当前在线会话将失效。",
                "ERROR",
                "ACCOUNT_DISABLED:" + userId + ":" + TimeSupport.now(),
                Map.of("operator", fallback(operator, "")),
                operator
        );
    }

    @Override
    public void sessionForcedOffline(String tenantId, Long userId, String operator, Map<String, Object> payload) {
        publishAccountSecurity(
                tenantId,
                userId,
                "SESSION_FORCED_OFFLINE",
                "登录会话已被强制下线",
                "你的一个登录会话已被 " + fallback(operator, "管理员") + " 强制下线。",
                "WARNING",
                "SESSION_FORCED_OFFLINE:" + userId + ":" + payloadValue(payload, "sessionId", TimeSupport.now().toString()),
                payload == null ? Map.of() : payload,
                operator
        );
    }

    @Override
    public void systemNoticePublished(String tenantId, Long noticeId, String title, String content, String operator) {
        if (!StringUtils.hasText(tenantId) || noticeId == null) {
            return;
        }
        Runnable publishTask = () -> sseRegistry.sendTenant(
                tenantId,
                NotificationView.systemNoticeBroadcast(
                        noticeId,
                        fallback(title, "公告"),
                        limit(fallback(stripHtml(content), "请查看系统公告详情。"), 500),
                        TimeSupport.now()));
        runAfterCommit(publishTask);
    }

    private void workflowTaskDecision(WorkflowTaskDecisionEvent event, String scenarioCode, String titlePrefix, String actorLabel, String level) {
        if (event == null || event.starterUserId() == null) {
            return;
        }
        String title = titlePrefix + fallback(event.instanceTitle(), "流程实例");
        String content = "流程编号：" + fallback(event.businessKey(), String.valueOf(event.instanceId()))
                + "\n" + actorLabel + "：" + fallback(event.operator(), "未知")
                + "\n当前节点：" + fallback(event.stepName(), "待处理")
                + "\n流程状态：" + (event.ended() ? "已结束" : "流转中");
        publishAfterCommit(new NotificationPublishCommand(
                event.tenantId(),
                scenarioCode,
                "WORKFLOW_TASK",
                String.valueOf(event.taskId()),
                "WORKFLOW_INSTANCE",
                String.valueOf(event.instanceId()),
                Set.of(event.starterUserId()),
                Set.of(),
                false,
                Map.of(
                        "instanceId", event.instanceId(),
                        "taskId", event.taskId(),
                        "ended", event.ended()
                ),
                title,
                content,
                level,
                WORKFLOW_INSTANCE_LINK,
                Map.of("route", WORKFLOW_INSTANCE_LINK, "instanceId", event.instanceId()),
                Map.of("instanceId", event.instanceId(), "taskId", event.taskId()),
                scenarioCode + ":" + event.taskId(),
                null,
                event.operator()
        ));
    }

    private void workflowInstanceClosed(WorkflowInstanceClosedEvent event, String scenarioCode, String titlePrefix, String actorLabel, String level) {
        if (event == null || event.emptyRecipients()) {
            return;
        }
        String title = titlePrefix + fallback(event.instanceTitle(), "流程实例");
        String content = "流程编号：" + fallback(event.businessKey(), String.valueOf(event.instanceId()))
                + "\n" + actorLabel + "：" + fallback(event.operator(), "未知")
                + "\n相关待办已结束";
        publishAfterCommit(new NotificationPublishCommand(
                event.tenantId(),
                scenarioCode,
                "WORKFLOW_INSTANCE",
                String.valueOf(event.instanceId()),
                "WORKFLOW_INSTANCE",
                String.valueOf(event.instanceId()),
                event.recipientUserIds(),
                event.recipientRoleCodes(),
                false,
                Map.of("instanceId", event.instanceId()),
                title,
                content,
                level,
                WORKFLOW_INSTANCE_LINK,
                Map.of("route", WORKFLOW_INSTANCE_LINK, "instanceId", event.instanceId()),
                Map.of("instanceId", event.instanceId()),
                scenarioCode + ":" + event.instanceId(),
                null,
                event.operator()
        ));
    }

    private void publishAccountSecurity(
            String tenantId,
            Long userId,
            String scenarioCode,
            String title,
            String content,
            String level,
            String dedupKey,
            Map<String, Object> metadata,
            String operator
    ) {
        if (!StringUtils.hasText(tenantId) || userId == null) {
            return;
        }
        Map<String, Object> safeMetadata = new LinkedHashMap<>();
        if (metadata != null) {
            safeMetadata.putAll(metadata);
        }
        safeMetadata.put("userId", userId);
        publishAfterCommit(new NotificationPublishCommand(
                tenantId,
                scenarioCode,
                "ACCOUNT_SECURITY",
                String.valueOf(userId),
                "ACCOUNT_SECURITY",
                String.valueOf(userId),
                Set.of(userId),
                Set.of(),
                false,
                safeMetadata,
                title,
                content,
                level,
                ACCOUNT_PROFILE_LINK,
                Map.of("route", ACCOUNT_PROFILE_LINK),
                safeMetadata,
                dedupKey,
                null,
                operator
        ));
    }

    private SysUserEntity loadUserByUsername(String tenantId, String username) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(username)) {
            return null;
        }
        return userAuthenticationFacade.findActiveEntity(tenantId, username).orElse(null);
    }

    private void publishAfterCommit(NotificationPublishCommand command) {
        if (command == null) {
            return;
        }
        // 业务事务内写入 Outbox，提交后异步投递，避免请求线程扇出与长事务
        outboxWriter.enqueue(
                OutboxWriter.TYPE_NOTIFICATION_PUBLISH,
                command.tenantId(),
                command.sourceType(),
                command.sourceId(),
                command
        );
        runAfterCommit(outboxDispatchWorker::triggerAsync);
    }

    private void runAfterCommit(Runnable publishTask) {
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

    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String payloadValue(Map<String, Object> payload, String key, String fallback) {
        if (payload == null || !payload.containsKey(key) || payload.get(key) == null) {
            return fallback;
        }
        return String.valueOf(payload.get(key));
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String stripHtml(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p>", "\n")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

}
