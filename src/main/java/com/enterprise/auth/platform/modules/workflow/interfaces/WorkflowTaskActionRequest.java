package com.enterprise.auth.platform.modules.workflow.interfaces;

import com.enterprise.auth.platform.modules.workflow.application.WorkflowTaskCommand;
import jakarta.validation.constraints.Size;

public record WorkflowTaskActionRequest(@Size(max = 500) String comment) {
    WorkflowTaskCommand toCommand() {
        return new WorkflowTaskCommand(comment);
    }
}