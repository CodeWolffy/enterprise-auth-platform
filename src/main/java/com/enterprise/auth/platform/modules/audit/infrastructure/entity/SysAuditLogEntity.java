package com.enterprise.auth.platform.modules.audit.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_audit_log")
@Data
public class SysAuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String eventType;
    private String operator;
    private String payloadJson;
    private String requestId;
    private String clientIp;
    private LocalDateTime occurredAt;
}
