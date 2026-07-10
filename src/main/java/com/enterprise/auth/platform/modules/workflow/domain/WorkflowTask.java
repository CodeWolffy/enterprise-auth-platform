package com.enterprise.auth.platform.modules.workflow.domain;

import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkflowTask {

    private Long id;
    private String tenantId;
    private Long instanceId;
    private Long definitionId;
    private Integer stepIndex;
    private String stepName;
    private WorkflowTaskStatus status;
    private Set<Long> candidateUserIds = Set.of();
    private Set<String> candidateGroupCodes = Set.of();
    private Long assigneeUserId;
    private String assigneeUsername;
    private String comment;
    private Instant createdAt;
    private Instant completedAt;
    private Instant updatedAt;
}
