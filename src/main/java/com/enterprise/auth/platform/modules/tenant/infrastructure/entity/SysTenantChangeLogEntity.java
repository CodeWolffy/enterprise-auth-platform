package com.enterprise.auth.platform.modules.tenant.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("sys_tenant_change_log")
@Data
public class SysTenantChangeLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String changeType;
    private String fieldKey;
    private String oldValue;
    private String newValue;
    private String summary;
    @TableField(fill = FieldFill.INSERT)
    private String operator;
    @TableField(fill = FieldFill.INSERT)
    private Instant occurredAt;
}
