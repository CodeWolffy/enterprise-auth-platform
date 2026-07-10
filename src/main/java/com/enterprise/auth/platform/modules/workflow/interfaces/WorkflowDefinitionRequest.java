package com.enterprise.auth.platform.modules.workflow.interfaces;

import com.enterprise.auth.platform.modules.workflow.application.WorkflowDefinitionCommand;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowStepDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record WorkflowDefinitionRequest(
        @NotBlank @Size(max = 128) String definitionKey,
        @NotBlank @Size(max = 128) String definitionName,
        @NotEmpty @Valid List<WorkflowStepDefinition> steps,
        @Size(max = 255) String remark
) {
    WorkflowDefinitionCommand toCommand() {
        return new WorkflowDefinitionCommand(definitionKey, definitionName, steps, remark);
    }
}
