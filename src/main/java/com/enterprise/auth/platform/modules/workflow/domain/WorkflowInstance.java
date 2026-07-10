package com.enterprise.auth.platform.modules.workflow.domain;

import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkflowInstance {

    private Long id;
    private String tenantId;
    private Long definitionId;
    private String definitionKey;
    private Integer definitionVersion;
    private String businessKey;
    private String title;
    private WorkflowInstanceStatus status;
    private Long starterUserId;
    private String starterUsername;
    private Integer currentStepIndex;
    private Map<String, Object> variablesSnapshot = Map.of();
    private Instant startedAt;
    private Instant endedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
