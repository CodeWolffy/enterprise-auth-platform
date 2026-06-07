package com.enterprise.auth.platform.modules.role.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@TableName("sys_role_menu")
@Data
public class SysRoleMenuEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private Long roleId;
    private Long menuId;
    private String createdBy;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}