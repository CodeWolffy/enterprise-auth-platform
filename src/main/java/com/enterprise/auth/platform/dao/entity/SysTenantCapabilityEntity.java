package com.enterprise.auth.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_tenant_capability")
@Data
public class SysTenantCapabilityEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String capabilityCode;
    private String capabilityName;
    private String capabilityDesc;
    private Integer sortOrder;
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
