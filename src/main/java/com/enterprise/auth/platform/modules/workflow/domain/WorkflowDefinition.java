package com.enterprise.auth.platform.modules.workflow.domain;

import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkflowDefinition {

    private Long id;
    private String tenantId;
    private String definitionKey;
    private String definitionName;
    private Integer version;
    private WorkflowDefinitionStatus status;
    private List<WorkflowStepDefinition> steps = List.of();
    private String remark;
    private Instant createdAt;
    private Instant updatedAt;
}
