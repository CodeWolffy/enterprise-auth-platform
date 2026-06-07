package com.enterprise.auth.platform.modules.tenant.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@TableName("sys_tenant_capability_resource_scope")
@Data
public class SysTenantCapabilityResourceScopeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String capabilityCode;
    private String resourceKey;
    private String scopeType;
    private Integer required;
    private String createdBy;
    private String updatedBy;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}