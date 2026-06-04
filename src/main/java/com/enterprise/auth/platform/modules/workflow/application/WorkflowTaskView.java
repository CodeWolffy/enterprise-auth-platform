package com.enterprise.auth.platform.modules.workflow.application;

import java.util.Set;

public record WorkflowTaskView(
        Long id,
        String tenantId,
        Long instanceId,
        Long definitionId,
        Integer stepIndex,
        String stepName,
        String status,
        Set<Long> candidateUserIds,
        Set<String> candidateGroupCodes,
        Long assigneeUserId,
        String assigneeUsername,
        String comment,
        Long createdAt,
        Long completedAt,
        boolean actionable,
        int urgeCount
) {
}