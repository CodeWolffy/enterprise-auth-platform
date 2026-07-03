package com.enterprise.auth.platform.modules.log.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_log")
public class SysLogEntity extends Model<SysLogEntity> {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantId;

    private String eventType;

    private String operator;

    private String payloadJson;

    private String requestId;

    private String clientIp;

    private String location;

    private String method;

    private String requestUri;

    private String requestParams;

    private Long requestTime;

    private String status;

    private String exMsg;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableLogic(value = "0", delval = "1")
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
