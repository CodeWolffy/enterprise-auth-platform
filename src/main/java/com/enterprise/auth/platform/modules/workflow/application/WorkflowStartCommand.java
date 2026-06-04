package com.enterprise.auth.platform.modules.workflow.application;

import java.util.Map;

public record WorkflowStartCommand(
        String definitionKey,
        String businessKey,
        String title,
        Map<String, Object> variables
) {
}