package com.enterprise.auth.platform.modules.tenant.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("sys_tenant")
@Data
public class SysTenantEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String tenantName;
    private Integer platformLevel;
    private Integer tenantStatus;
    private Instant authBeginAt;
    private Instant expireAt;
    private String packageCode;
    private String logoUrl;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String website;
    private String address;
    private String lifecycleNote;
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
