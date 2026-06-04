package com.enterprise.auth.platform.modules.workflow.interfaces;

import com.enterprise.auth.platform.modules.workflow.application.WorkflowTaskTransferCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record WorkflowTaskTransferRequest(
        @NotNull @Positive Long targetUserId,
        @Size(max = 500) String comment
) {
    WorkflowTaskTransferCommand toCommand() {
        return new WorkflowTaskTransferCommand(targetUserId, comment);
    }
}