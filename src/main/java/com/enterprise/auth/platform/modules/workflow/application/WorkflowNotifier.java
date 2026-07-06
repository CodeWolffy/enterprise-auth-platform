package com.enterprise.auth.platform.modules.workflow.application;

import com.enterprise.auth.platform.modules.log.application.LogPublisher;
import com.enterprise.auth.platform.modules.notification.application.NotificationScenarioPublisher;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade.EnabledUser;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstanceStatus;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessInstanceEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskEntity;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 工作流通知与审计发布：待办创建、审批决策、转签、实例关闭四类场景通知，
 * 以及对应的操作审计日志落库。
 */
@Component
class WorkflowNotifier {

    private final NotificationScenarioPublisher notificationScenarioPublisher;
    private final LogPublisher logPublisher;
    private final WorkflowStore store;

    WorkflowNotifier(
            NotificationScenarioPublisher notificationScenarioPublisher,
            LogPublisher logPublisher,
            WorkflowStore store
    ) {
        this.notificationScenarioPublisher = notificationScenarioPublisher;
        this.logPublisher = logPublisher;
        this.store = store;
    }

    void publishWorkflowTodoCreated(String tenantId, WfProcessInstanceEntity instance, WfTaskEntity task, String operator) {
        WorkflowRecipients recipients = store.notificationRecipients(task);
        notificationScenarioPublisher.workflowTodoCreated(new NotificationScenarioPublisher.WorkflowTodoCreatedEvent(
                tenantId,
                instance.getId(),
                instance.getTitle(),
                instance.getBusinessKey(),
                task.getId(),
                task.getStepName(),
                recipients.userIds(),
                recipients.roleCodes(),
                operator
        ));
    }

    void publishWorkflowTaskDecision(
            String tenantId,
            WfProcessInstanceEntity instance,
            WfTaskEntity task,
            String operator,
            boolean approved
    ) {
        NotificationScenarioPublisher.WorkflowTaskDecisionEvent event = new NotificationScenarioPublisher.WorkflowTaskDecisionEvent(
                tenantId,
                instance.getId(),
                instance.getTitle(),
                instance.getBusinessKey(),
                instance.getStarterUserId(),
                task.getId(),
                task.getStepName(),
                operator,
                !WorkflowInstanceStatus.RUNNING.name().equals(instance.getStatus())
        );
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
            WfProcessInstanceEntity instance,
            WfTaskEntity originalTask,
            WfTaskEntity newTask,
            EnabledUser targetUser,
            String operator
    ) {
        notificationScenarioPublisher.workflowTaskTransferred(new NotificationScenarioPublisher.WorkflowTaskTransferEvent(
                tenantId,
                instance.getId(),
                instance.getTitle(),
                instance.getBusinessKey(),
                instance.getStarterUserId(),
                originalTask.getId(),
                newTask.getId(),
                newTask.getStepName(),
                targetUser.id(),
                targetUser.username(),
                operator
        ));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("targetUserId", targetUser.id());
        extra.put("targetUsername", targetUser.username());
        extra.put("newTaskId", newTask.getId());
        publishWorkflowAudit("WORKFLOW_TASK_TRANSFERRED", tenantId, operator, instance, originalTask, extra);
    }

    void publishWorkflowInstanceClosed(
            String tenantId,
            WfProcessInstanceEntity instance,
            WorkflowRecipients recipients,
            Long operatorUserId,
            String operator,
            boolean withdrawn
    ) {
        Set<Long> userIds = new LinkedHashSet<>(recipients.userIds());
        if (instance.getStarterUserId() != null && !Objects.equals(instance.getStarterUserId(), operatorUserId)) {
            userIds.add(instance.getStarterUserId());
        }
        NotificationScenarioPublisher.WorkflowInstanceClosedEvent event = new NotificationScenarioPublisher.WorkflowInstanceClosedEvent(
                tenantId,
                instance.getId(),
                instance.getTitle(),
                instance.getBusinessKey(),
                userIds,
                recipients.roleCodes(),
                operator
        );
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
            WfProcessInstanceEntity instance,
            WfTaskEntity task,
            Map<String, Object> extra
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("instanceId", instance.getId());
        details.put("definitionId", instance.getDefinitionId());
        details.put("definitionKey", instance.getDefinitionKey());
        details.put("definitionVersion", instance.getDefinitionVersion());
        details.put("businessKey", instance.getBusinessKey());
        details.put("title", instance.getTitle());
        details.put("instanceStatus", instance.getStatus());
        details.put("currentStepIndex", instance.getCurrentStepIndex());
        if (task != null) {
            details.put("taskId", task.getId());
            details.put("stepIndex", task.getStepIndex());
            details.put("stepName", task.getStepName());
            details.put("taskStatus", task.getStatus());
        }
        if (extra != null && !extra.isEmpty()) {
            details.putAll(extra);
        }
        logPublisher.publish(eventType, operator, tenantId, details);
    }
}
