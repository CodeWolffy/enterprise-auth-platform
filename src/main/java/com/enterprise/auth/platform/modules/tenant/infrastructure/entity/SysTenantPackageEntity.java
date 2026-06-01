package com.enterprise.auth.platform.modules.tenant.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_tenant_package")
@Data
public class SysTenantPackageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String packageCode;
    private String packageName;
    private Integer userQuota;
    private Integer storageQuotaGb;
    private String packageDesc;
    private Integer enabled;
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
