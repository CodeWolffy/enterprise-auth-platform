package com.enterprise.auth.platform.modules.workflow.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@TableName("wf_process_instance")
@Data
public class WfProcessInstanceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private Long definitionId;
    private String definitionKey;
    private Integer definitionVersion;
    private String businessKey;
    private String title;
    private String status;
    private Long starterUserId;
    private String starterUsername;
    private Integer currentStepIndex;
    private String variablesSnapshotJson;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}