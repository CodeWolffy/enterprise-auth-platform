package com.enterprise.auth.platform.modules.workflow.application;

import java.util.List;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowStepDefinition;

public record WorkflowDefinitionCommand(
        String definitionKey,
        String definitionName,
        List<WorkflowStepDefinition> steps,
        String remark
) {
}
