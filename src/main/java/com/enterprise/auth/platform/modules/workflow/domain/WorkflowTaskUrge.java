package com.enterprise.auth.platform.modules.workflow.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkflowTaskUrge {

    private Long id;
    private String tenantId;
    private Long taskId;
    private Long instanceId;
    private Long urgedByUserId;
    private String urgedByUsername;
    private String comment;
    private Instant urgedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
