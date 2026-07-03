package com.enterprise.auth.platform.modules.system.infrastructure.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@TableName("sys_mail_channel")
@Data
public class SysMailChannelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String provider;
    private String mailHost;
    private Integer mailPort;
    private String mailUsername;
    @JsonIgnore
    private String mailPassword;
    private String mailFrom;
    private String mailProtocol;
    private Integer useSsl;
    private Integer useStarttls;
    private Integer enabled;
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