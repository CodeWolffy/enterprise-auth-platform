package com.enterprise.auth.platform.modules.workflow.application;

import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowDefinition;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowInstance;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRejectStrategy;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowStepDefinition;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTask;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class WorkflowViewMapper {

    private final WorkflowStore store;
    private final WorkflowTaskUrgeService urgeService;

    WorkflowViewMapper(WorkflowStore store, WorkflowTaskUrgeService urgeService) {
        this.store = store;
        this.urgeService = urgeService;
    }

    WorkflowDefinitionView toDefinitionView(WorkflowDefinition definition) {
        List<WorkflowStepView> stepViews = new ArrayList<>();
        List<WorkflowStepDefinition> steps = definition.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            WorkflowStepDefinition step = steps.get(i);
            stepViews.add(new WorkflowStepView(
                    i,
                    step.name(),
                    step.candidateUserIds(),
                    step.candidateGroupCodes(),
                    step.rejectStrategy() == null ? WorkflowRejectStrategy.END.name() : step.rejectStrategy().name(),
                    step.rejectTarget()
            ));
        }
        return new WorkflowDefinitionView(
                definition.getId(),
                definition.getTenantId(),
                definition.getDefinitionKey(),
                definition.getDefinitionName(),
                definition.getVersion(),
                definition.getStatus().name(),
                stepViews,
                definition.getRemark(),
                definition.getCreatedAt(),
                definition.getUpdatedAt()
        );
    }

    WorkflowInstanceView toInstanceView(WorkflowInstance instance) {
        return new WorkflowInstanceView(
                instance.getId(),
                instance.getTenantId(),
                instance.getDefinitionId(),
                instance.getDefinitionKey(),
                instance.getDefinitionVersion(),
                instance.getBusinessKey(),
                instance.getTitle(),
                instance.getStatus().name(),
                instance.getStarterUserId(),
                instance.getStarterUsername(),
                instance.getCurrentStepIndex(),
                instance.getVariablesSnapshot(),
                instance.getStartedAt(),
                instance.getEndedAt()
        );
    }

    WorkflowTaskView toTaskView(WorkflowTask task, UserAccount user) {
        return toTaskView(task, user, urgeService.countUrges(task.getTenantId(), task.getId()));
    }

    WorkflowTaskView toTaskView(WorkflowTask task, UserAccount user, int urgeCount) {
        return new WorkflowTaskView(
                task.getId(),
                task.getTenantId(),
                task.getInstanceId(),
                task.getDefinitionId(),
                task.getStepIndex(),
                task.getStepName(),
                task.getStatus().name(),
                task.getCandidateUserIds(),
                task.getCandidateGroupCodes(),
                task.getAssigneeUserId(),
                task.getAssigneeUsername(),
                task.getComment(),
                task.getCreatedAt(),
                task.getCompletedAt(),
                task.getStatus() == WorkflowTaskStatus.PENDING && store.isActionable(task, user),
                urgeCount
        );
    }

    List<WorkflowTaskView> toTaskViews(Collection<WorkflowTask> tasks, UserAccount user, Map<Long, Long> urgeCounts) {
        return tasks.stream()
                .map(task -> toTaskView(
                        task,
                        user,
                        Math.toIntExact(urgeCounts.getOrDefault(task.getId(), 0L))
                ))
                .toList();
    }
}