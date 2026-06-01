package com.enterprise.auth.platform.modules.resource.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_resource")
@Data
public class SysResourceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private Long parentId;
    private String ancestors;
    private String resourceType;
    private String resourceKey;
    private String resourceName;
    private String routeKey;
    private String grantKey;
    private String path;
    private String component;
    private String icon;
    private Integer orderNo;
    private Integer visible;
    private Integer enabled;
    private Integer isSystem;
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
