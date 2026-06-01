package com.enterprise.auth.platform.modules.audit.application;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "审计日志条目（跨模块查询视图）")
public record AuditLogEntry(
        @Schema(description = "事件类型") String eventType,
        @Schema(description = "操作人") String operator,
        @Schema(description = "发生时间（毫秒时间戳）") Long occurredAtMs,
        @Schema(description = "事件明细（JSON）") String payloadJson
) {
}