package com.enterprise.auth.platform.modules.tenant.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_tenant_package_capability")
@Data
public class SysTenantPackageCapabilityEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String packageCode;
    private String capabilityCode;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
