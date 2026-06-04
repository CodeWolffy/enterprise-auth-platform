package com.enterprise.auth.platform.modules.workflow.application;

import com.enterprise.auth.platform.modules.workflow.domain.WorkflowRejectStrategy;
import java.util.Set;

public record WorkflowStepDefinition(
        String name,
        Set<Long> candidateUserIds,
        Set<String> candidateGroupCodes,
        WorkflowRejectStrategy rejectStrategy
) {
    public WorkflowStepDefinition {
        candidateUserIds = candidateUserIds == null ? Set.of() : Set.copyOf(candidateUserIds);
        candidateGroupCodes = candidateGroupCodes == null ? Set.of() : Set.copyOf(candidateGroupCodes);
        rejectStrategy = rejectStrategy == null ? WorkflowRejectStrategy.END : rejectStrategy;
    }

    public WorkflowStepDefinition(String name, Set<Long> candidateUserIds, Set<String> candidateGroupCodes) {
        this(name, candidateUserIds, candidateGroupCodes, WorkflowRejectStrategy.END);
    }
}