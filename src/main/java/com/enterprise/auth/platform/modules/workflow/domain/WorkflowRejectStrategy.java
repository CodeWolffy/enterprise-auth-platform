package com.enterprise.auth.platform.modules.workflow.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum WorkflowRejectStrategy {
    END,
    PREVIOUS,
    RESTART,
    TO_STEP,
    TO_STARTER;

    @JsonCreator
    public static WorkflowRejectStrategy fromValue(String value) {
        if (value == null || value.isBlank()) {
            return END;
        }
        return switch (value.trim().toUpperCase()) {
            case "END" -> END;
            case "PREVIOUS" -> PREVIOUS;
            case "RESTART" -> RESTART;
            case "TO_STEP" -> TO_STEP;
            case "TO_STARTER" -> TO_STARTER;
            default -> END;
        };
    }
}
