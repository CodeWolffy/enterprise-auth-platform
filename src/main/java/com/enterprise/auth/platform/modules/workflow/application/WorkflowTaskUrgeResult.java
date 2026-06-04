package com.enterprise.auth.platform.modules.workflow.application;

public record WorkflowTaskUrgeResult(
        WorkflowTaskUrgeView urge,
        int totalUrgeCount,
        WorkflowInstanceView instance
) {
}