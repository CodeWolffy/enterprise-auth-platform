package com.enterprise.auth.platform.modules.workflow.application;

import java.util.Set;

public record WorkflowStepView(
        int stepIndex,
        String name,
        Set<Long> candidateUserIds,
        Set<String> candidateGroupCodes,
        String rejectStrategy,
        Integer rejectTarget
) {
}