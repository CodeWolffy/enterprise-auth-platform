package com.enterprise.auth.platform.modules.system.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@TableName("sys_outbox_event")
@Data
public class SysOutboxEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String eventType;
    private String aggregateType;
    private String aggregateId;
    private String payloadJson;
    private String status;
    private Integer attempts;
    private Integer maxAttempts;
    private Instant nextRetryAt;
    private String lastError;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}