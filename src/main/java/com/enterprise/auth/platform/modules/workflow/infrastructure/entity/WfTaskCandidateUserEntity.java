package com.enterprise.auth.platform.modules.workflow.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@TableName("wf_task_candidate_user")
@Data
public class WfTaskCandidateUserEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private Long taskId;
    private Long userId;
    private Instant createdAt;
}