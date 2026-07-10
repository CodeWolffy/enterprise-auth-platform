package com.enterprise.auth.platform.modules.workflow.domain;

import java.util.Set;

public record WorkflowStepDefinition(
        String name,
        Set<Long> candidateUserIds,
        Set<String> candidateGroupCodes,
        WorkflowRejectStrategy rejectStrategy,
        Integer rejectTarget
) {
    public WorkflowStepDefinition {
        candidateUserIds = candidateUserIds == null ? Set.of() : Set.copyOf(candidateUserIds);
        candidateGroupCodes = candidateGroupCodes == null ? Set.of() : Set.copyOf(candidateGroupCodes);
        rejectStrategy = rejectStrategy == null ? WorkflowRejectStrategy.END : rejectStrategy;
        rejectTarget = rejectStrategy == WorkflowRejectStrategy.TO_STEP ? rejectTarget : null;
    }

    public WorkflowStepDefinition(String name, Set<Long> candidateUserIds, Set<String> candidateGroupCodes) {
        this(name, candidateUserIds, candidateGroupCodes, WorkflowRejectStrategy.END, null);
    }

    public WorkflowStepDefinition(
            String name,
            Set<Long> candidateUserIds,
            Set<String> candidateGroupCodes,
            WorkflowRejectStrategy rejectStrategy
    ) {
        this(name, candidateUserIds, candidateGroupCodes, rejectStrategy, null);
    }
}
