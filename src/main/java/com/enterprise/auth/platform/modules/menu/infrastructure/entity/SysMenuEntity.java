package com.enterprise.auth.platform.modules.menu.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@TableName("sys_menu")
@Data
public class SysMenuEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private Long parentId;
    private String ancestors;
    private String menuType;
    private String resourceKey;
    private String menuName;
    private String routeKey;
    private String grantKey;
    private String path;
    private String component;
    private String redirect;
    private String icon;
    private Integer orderNo;
    private Integer visible;
    private Integer enabled;
    private Integer isSystem;
    private Integer outerStatus;
    private String applicationKey;
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