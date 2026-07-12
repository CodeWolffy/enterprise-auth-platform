package com.enterprise.auth.platform.modules.workflow.application;

import com.enterprise.auth.platform.modules.log.application.LogPublisher;
import com.enterprise.auth.platform.common.notification.NotificationScenarioPort;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade.EnabledUser;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstance;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstanceStatus;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTask;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class WorkflowNotifier {

    private final NotificationScenarioPort notificationScenarioPublisher;
    private final LogPublisher logPublisher;
    private final WorkflowStore store;

    WorkflowNotifier(
            NotificationScenarioPort notificationScenarioPublisher,
            LogPublisher logPublisher,
            WorkflowStore store
    ) {
        this.notificationScenarioPublisher = notificationScenarioPublisher;
        this.logPublisher = logPublisher;
        this.store = store;
    }

    void publishWorkflowTodoCreated(String tenantId, WorkflowInstance instance, WorkflowTask task, String operator) {
        WorkflowRecipients recipients = store.notificationRecipients(task);
        notificationScenarioPublisher.workflowTodoCreated(new NotificationScenarioPort.WorkflowTodoCreatedEvent(
                tenantId, instance.getId(), instance.getTitle(), instance.getBusinessKey(),
                task.getId(), task.getStepName(), recipients.userIds(), recipients.roleCodes(), operator));
    }

    void publishWorkflowTaskDecision(
            String tenantId, WorkflowInstance instance, WorkflowTask task, String operator, boolean approved) {
        NotificationScenarioPort.WorkflowTaskDecisionEvent event =
                new NotificationScenarioPort.WorkflowTaskDecisionEvent(
                        tenantId, instance.getId(), instance.getTitle(), instance.getBusinessKey(),
                        instance.getStarterUserId(), task.getId(), task.getStepName(), operator,
                        instance.getStatus() != WorkflowInstanceStatus.RUNNING);
        if (approved) {
            notificationScenarioPublisher.workflowTaskApproved(event);
            publishWorkflowAudit("WORKFLOW_TASK_APPROVED", tenantId, operator, instance, task, null);
        } else {
            notificationScenarioPublisher.workflowTaskRejected(event);
            publishWorkflowAudit("WORKFLOW_TASK_REJECTED", tenantId, operator, instance, task, null);
        }
    }

    void publishWorkflowTaskTransferred(
            String tenantId,
            WorkflowInstance instance,
            WorkflowTask originalTask,
            WorkflowTask newTask,
            EnabledUser targetUser,
            String operator
    ) {
        notificationScenarioPublisher.workflowTaskTransferred(
                new NotificationScenarioPort.WorkflowTaskTransferEvent(
                        tenantId, instance.getId(), instance.getTitle(), instance.getBusinessKey(),
                        instance.getStarterUserId(), originalTask.getId(), newTask.getId(), newTask.getStepName(),
                        targetUser.id(), targetUser.username(), operator));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("targetUserId", targetUser.id());
        extra.put("targetUsername", targetUser.username());
        extra.put("newTaskId", newTask.getId());
        publishWorkflowAudit("WORKFLOW_TASK_TRANSFERRED", tenantId, operator, instance, originalTask, extra);
    }

    void publishWorkflowInstanceClosed(
            String tenantId,
            WorkflowInstance instance,
            WorkflowRecipients recipients,
            Long operatorUserId,
            String operator,
            boolean withdrawn
    ) {
        Set<Long> userIds = new LinkedHashSet<>(recipients.userIds());
        if (instance.getStarterUserId() != null && !Objects.equals(instance.getStarterUserId(), operatorUserId)) {
            userIds.add(instance.getStarterUserId());
        }
        NotificationScenarioPort.WorkflowInstanceClosedEvent event =
                new NotificationScenarioPort.WorkflowInstanceClosedEvent(
                        tenantId, instance.getId(), instance.getTitle(), instance.getBusinessKey(),
                        userIds, recipients.roleCodes(), operator);
        if (withdrawn) {
            notificationScenarioPublisher.workflowInstanceWithdrawn(event);
            publishWorkflowAudit("WORKFLOW_INSTANCE_WITHDRAWN", tenantId, operator, instance, null, null);
        } else {
            notificationScenarioPublisher.workflowInstanceTerminated(event);
            publishWorkflowAudit("WORKFLOW_INSTANCE_TERMINATED", tenantId, operator, instance, null, null);
        }
    }

    private void publishWorkflowAudit(
            String eventType,
            String tenantId,
            String operator,
            WorkflowInstance instance,
            WorkflowTask task,
            Map<String, Object> extra
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("instanceId", instance.getId());
        details.put("definitionId", instance.getDefinitionId());
        details.put("definitionKey", instance.getDefinitionKey());
        details.put("definitionVersion", instance.getDefinitionVersion());
        details.put("businessKey", instance.getBusinessKey());
        details.put("title", instance.getTitle());
        details.put("instanceStatus", instance.getStatus().name());
        details.put("currentStepIndex", instance.getCurrentStepIndex());
        if (task != null) {
            details.put("taskId", task.getId());
            details.put("stepIndex", task.getStepIndex());
            details.put("stepName", task.getStepName());
            details.put("taskStatus", task.getStatus().name());
        }
        if (extra != null && !extra.isEmpty()) {
            details.putAll(extra);
        }
        logPublisher.publish(eventType, operator, tenantId, details);
    }
}
