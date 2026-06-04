package com.enterprise.auth.platform.modules.workflow.application;

import java.util.Set;

public record WorkflowStepDefinition(
        String name,
        Set<Long> candidateUserIds,
        Set<String> candidateGroupCodes
) {
    public WorkflowStepDefinition {
        candidateUserIds = candidateUserIds == null ? Set.of() : Set.copyOf(candidateUserIds);
        candidateGroupCodes = candidateGroupCodes == null ? Set.of() : Set.copyOf(candidateGroupCodes);
    }
}