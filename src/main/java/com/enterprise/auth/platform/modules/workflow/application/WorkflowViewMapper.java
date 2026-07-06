package com.enterprise.auth.platform.modules.workflow.application;

import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRejectStrategy;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessDefinitionEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfProcessInstanceEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskEntity;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 工作流实体到视图对象的映射：流程定义、流程实例、任务。
 * 任务视图的催办计数与可处理标记分别委托催办服务与候选处理人判定。
 */
@Component
class WorkflowViewMapper {

    private final WorkflowCodec codec;
    private final WorkflowStore store;
    private final WorkflowTaskUrgeService urgeService;

    WorkflowViewMapper(WorkflowCodec codec, WorkflowStore store, WorkflowTaskUrgeService urgeService) {
        this.codec = codec;
        this.store = store;
        this.urgeService = urgeService;
    }

    WorkflowDefinitionView toDefinitionView(WfProcessDefinitionEntity entity) {
        List<WorkflowStepDefinition> steps = codec.readSteps(entity.getStepsJson());
        List<WorkflowStepView> stepViews = new ArrayList<>();
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
                entity.getId(),
                entity.getTenantId(),
                entity.getDefinitionKey(),
                entity.getDefinitionName(),
                entity.getVersion(),
                entity.getStatus(),
                stepViews,
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    WorkflowInstanceView toInstanceView(WfProcessInstanceEntity entity) {
        return new WorkflowInstanceView(
                entity.getId(),
                entity.getTenantId(),
                entity.getDefinitionId(),
                entity.getDefinitionKey(),
                entity.getDefinitionVersion(),
                entity.getBusinessKey(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getStarterUserId(),
                entity.getStarterUsername(),
                entity.getCurrentStepIndex(),
                codec.readMap(entity.getVariablesSnapshotJson()),
                entity.getStartedAt(),
                entity.getEndedAt()
        );
    }

    WorkflowTaskView toTaskView(WfTaskEntity entity, UserAccount user) {
        int urgeCount = urgeService.countUrges(entity.getTenantId(), entity.getId());
        return new WorkflowTaskView(
                entity.getId(),
                entity.getTenantId(),
                entity.getInstanceId(),
                entity.getDefinitionId(),
                entity.getStepIndex(),
                entity.getStepName(),
                entity.getStatus(),
                codec.readLongSet(entity.getCandidateUserIdsJson()),
                codec.readStringSet(entity.getCandidateGroupCodesJson()),
                entity.getAssigneeUserId(),
                entity.getAssigneeUsername(),
                entity.getComment(),
                entity.getCreatedAt(),
                entity.getCompletedAt(),
                WorkflowTaskStatus.PENDING.name().equals(entity.getStatus()) && store.isActionable(entity, user),
                urgeCount
        );
    }
}
