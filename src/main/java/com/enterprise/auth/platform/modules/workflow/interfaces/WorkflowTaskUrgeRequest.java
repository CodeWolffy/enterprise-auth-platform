package com.enterprise.auth.platform.modules.workflow.interfaces;

import com.enterprise.auth.platform.modules.workflow.application.WorkflowTaskCommand;
import jakarta.validation.constraints.Size;

public record WorkflowTaskUrgeRequest(@Size(max = 500) String comment) {
    public WorkflowTaskCommand toCommand() {
        return new WorkflowTaskCommand(comment);
    }
}