package com.enterprise.auth.platform.modules.workflow.interfaces;

import com.enterprise.auth.platform.modules.workflow.application.WorkflowStartCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record WorkflowStartRequest(
        @NotBlank @Size(max = 128) String definitionKey,
        @NotBlank @Size(max = 128) String businessKey,
        @NotBlank @Size(max = 200) String title,
        Map<String, Object> variables
) {
    WorkflowStartCommand toCommand() {
        return new WorkflowStartCommand(definitionKey, businessKey, title, variables);
    }
}