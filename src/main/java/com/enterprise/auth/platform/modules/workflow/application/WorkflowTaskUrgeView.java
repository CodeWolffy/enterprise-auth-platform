package com.enterprise.auth.platform.modules.workflow.application;

import com.enterprise.auth.platform.modules.workflow.domain.WorkflowTaskUrge;
import java.time.Instant;
import java.util.Set;

public record WorkflowTaskUrgeView(
        Long id,
        Long taskId,
        Long instanceId,
        Long urgedByUserId,
        String urgedByUsername,
        String comment,
        Instant urgedAt,
        Set<String> targetUsernames
) {
    public static WorkflowTaskUrgeView from(WorkflowTaskUrge entity, Set<String> targetUsernames) {
        return new WorkflowTaskUrgeView(
                entity.getId(),
                entity.getTaskId(),
                entity.getInstanceId(),
                entity.getUrgedByUserId(),
                entity.getUrgedByUsername(),
                entity.getComment(),
                entity.getUrgedAt(),
                targetUsernames
        );
    }
}
