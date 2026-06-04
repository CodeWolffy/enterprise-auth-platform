package com.enterprise.auth.platform.modules.workflow.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@TableName("wf_task")
@Data
public class WfTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private Long instanceId;
    private Long definitionId;
    private Integer stepIndex;
    private String stepName;
    private String status;
    private String candidateUserIdsJson;
    private String candidateGroupCodesJson;
    private Long assigneeUserId;
    private String assigneeUsername;
    private String comment;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}