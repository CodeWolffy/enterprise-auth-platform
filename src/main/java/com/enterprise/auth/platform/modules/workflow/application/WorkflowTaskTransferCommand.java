package com.enterprise.auth.platform.modules.workflow.application;

public record WorkflowTaskTransferCommand(
        Long targetUserId,
        String comment
) {
}