package com.enterprise.auth.platform.modules.user.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

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
    private String avatarFileKey;
    private String passwordHash;
    private Integer enabled;
    private Integer sessionVersion;
    private Integer mustChangePassword;
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
    @TableLogic
    private Integer deleted;
    private Instant lastLoginAt;
    private String lastLoginIp;
    private Instant passwordUpdatedAt;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
