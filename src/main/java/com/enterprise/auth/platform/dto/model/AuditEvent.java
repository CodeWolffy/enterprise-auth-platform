package com.enterprise.auth.platform.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Map;

@Schema(description = "审计事件")
public record AuditEvent(
        @Schema(description = "事件类型") String type,
        @Schema(description = "操作人") String operator,
        @Schema(description = "租户编码") String tenantId,
        @Schema(description = "请求ID") String requestId,
        @Schema(description = "客户端IP") String clientIp,
        @Schema(description = "发生时间") Long occurredAt,
        @Schema(description = "事件明细") Map<String, Object> details
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
