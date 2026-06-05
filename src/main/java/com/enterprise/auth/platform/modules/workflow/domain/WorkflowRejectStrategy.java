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
            case "END", "REJECT_END" -> END;
            case "PREVIOUS", "BACK_PREVIOUS", "TO_PREVIOUS" -> PREVIOUS;
            case "RESTART", "START", "TO_START" -> RESTART;
            case "TO_STEP", "SPECIFIED", "SPECIFIED_STEP" -> TO_STEP;
            case "TO_STARTER", "STARTER", "APPLICANT", "TO_APPLICANT" -> TO_STARTER;
            default -> END;
        };
    }
}