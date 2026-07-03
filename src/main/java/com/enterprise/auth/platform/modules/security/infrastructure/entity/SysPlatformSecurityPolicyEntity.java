package com.enterprise.auth.platform.modules.security.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@TableName("sys_platform_security_policy")
@Data
public class SysPlatformSecurityPolicyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer passwordMinLength;
    private Integer passwordMaxLength;
    private Integer passwordRequireLetter;
    private Integer passwordRequireNumber;
    private Integer passwordRequireSpecial;
    private Integer passwordHistoryCount;
    private Integer passwordExpireDays;
    private Integer loginFailureMaxAttempts;
    private Integer loginFailureLockMinutes;
    private Integer loginFailureWindowMinutes;
    private Integer captchaEnabled;
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