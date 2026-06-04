package com.enterprise.auth.platform.modules.workflow.application;

public record WorkflowStartResult(
        WorkflowInstanceView instance,
        WorkflowTaskView currentTask
) {
}