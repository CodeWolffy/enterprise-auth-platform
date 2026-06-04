package com.enterprise.auth.platform.modules.workflow.application;

import java.util.Map;

public record WorkflowInstanceView(
        Long id,
        String tenantId,
        Long definitionId,
        String definitionKey,
        Integer definitionVersion,
        String businessKey,
        String title,
        String status,
        Long starterUserId,
        String starterUsername,
        Integer currentStepIndex,
        Map<String, Object> variablesSnapshot,
        Long startedAt,
        Long endedAt
) {
}