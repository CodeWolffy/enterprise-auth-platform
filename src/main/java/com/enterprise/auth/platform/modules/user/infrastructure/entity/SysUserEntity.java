package com.enterprise.auth.platform.modules.user.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_user")
@Data
public class SysUserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private Long deptId;
    private String username;
    private String displayName;
    private String mobile;
    private String email;
    private String passwordHash;
    private Integer enabled;
    private Integer sessionVersion;
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
    @TableLogic
    private Integer deleted;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime passwordUpdatedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
