package com.enterprise.auth.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_tenant_capability_override")
@Data
public class SysTenantCapabilityOverrideEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String capabilityCode;
    private Integer enabled;
    private String capabilityDescOverride;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
