package com.enterprise.auth.platform.common.notification;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * 通知场景端口：业务模块只依赖本接口，由 notification 模块实现，避免模块环。
 */
public interface NotificationScenarioPort {

    void workflowTodoCreated(WorkflowTodoCreatedEvent event);

    void workflowTaskApproved(WorkflowTaskDecisionEvent event);

    void workflowTaskRejected(WorkflowTaskDecisionEvent event);

    void workflowTaskTransferred(WorkflowTaskTransferEvent event);

    void workflowInstanceWithdrawn(WorkflowInstanceClosedEvent event);

    void workflowInstanceTerminated(WorkflowInstanceClosedEvent event);

    void accountLocked(String tenantId, String username, String clientIp);

    void passwordResetRequested(String tenantId, Long userId, String username, String clientIp);

    void passwordResetCompleted(String tenantId, Long userId, String username);

    void passwordChanged(String tenantId, Long userId, String username);

    void adminPasswordReset(String tenantId, Long userId, String username, String operator);

    void accountDisabled(String tenantId, Long userId, String username, String operator);

    void sessionForcedOffline(String tenantId, Long userId, String operator, Map<String, Object> payload);

    void systemNoticePublished(String tenantId, Long noticeId, String title, String content, String operator);

    record WorkflowTodoCreatedEvent(
            String tenantId,
            Long instanceId,
            String instanceTitle,
            String businessKey,
            Long taskId,
            String stepName,
            Set<Long> recipientUserIds,
            Set<String> recipientRoleCodes,
            String operator
    ) {
        public WorkflowTodoCreatedEvent {
            recipientUserIds = copyUserIds(recipientUserIds);
            recipientRoleCodes = copyRoleCodes(recipientRoleCodes);
        }

        public boolean emptyRecipients() {
            return recipientUserIds.isEmpty() && recipientRoleCodes.isEmpty();
        }
    }

    record WorkflowTaskDecisionEvent(
            String tenantId,
            Long instanceId,
            String instanceTitle,
            String businessKey,
            Long starterUserId,
            Long taskId,
            String stepName,
            String operator,
            boolean ended
    ) {
    }

    record WorkflowTaskTransferEvent(
            String tenantId,
            Long instanceId,
            String instanceTitle,
            String businessKey,
            Long starterUserId,
            Long originalTaskId,
            Long newTaskId,
            String stepName,
            Long targetUserId,
            String targetUsername,
            String operator
    ) {
    }

    record WorkflowInstanceClosedEvent(
            String tenantId,
            Long instanceId,
            String instanceTitle,
            String businessKey,
            Set<Long> recipientUserIds,
            Set<String> recipientRoleCodes,
            String operator
    ) {
        public WorkflowInstanceClosedEvent {
            recipientUserIds = copyUserIds(recipientUserIds);
            recipientRoleCodes = copyRoleCodes(recipientRoleCodes);
        }

        public boolean emptyRecipients() {
            return recipientUserIds.isEmpty() && recipientRoleCodes.isEmpty();
        }
    }

    private static Set<Long> copyUserIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> values = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null) {
                values.add(userId);
            }
        }
        return values;
    }

    private static Set<String> copyRoleCodes(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String roleCode : roleCodes) {
            if (StringUtils.hasText(roleCode)) {
                values.add(roleCode.trim());
            }
        }
        return values;
    }
}