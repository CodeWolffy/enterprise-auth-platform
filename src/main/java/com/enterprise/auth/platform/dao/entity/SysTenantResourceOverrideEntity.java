package com.enterprise.auth.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_tenant_resource_override")
@Data
public class SysTenantResourceOverrideEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private Long resourceId;
    private Integer enabled;
    private Integer visible;
    private Integer orderNo;
    private String titleOverride;
    private String iconOverride;
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
