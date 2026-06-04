package com.enterprise.auth.platform.modules.workflow.application;

public record WorkflowActionResult(
        WorkflowInstanceView instance,
        WorkflowTaskView nextTask
) {
}