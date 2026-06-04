package com.enterprise.auth.platform.modules.workflow.application;

import java.util.Map;

public record WorkflowDefinitionView(
        Long id,
        String tenantId,
        String definitionKey,
        String definitionName,
        Integer version,
        String status,
        java.util.List<WorkflowStepView> steps,
        String remark,
        Long createdAt,
        Long updatedAt
) {
}