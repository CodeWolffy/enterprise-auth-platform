package com.enterprise.auth.platform.modules.workflow.infrastructure.projection;

import lombok.Data;

/**
 * 当前页任务的催办聚合结果。
 */
@Data
public class WorkflowTaskUrgeCountProjection {

    private Long taskId;
    private Long urgeCount;
}
