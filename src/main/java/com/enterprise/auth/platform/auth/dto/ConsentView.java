package com.enterprise.auth.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "授权同意记录视图")
public record ConsentView(
        @Schema(description = "授权中心内部注册客户端 ID") String registeredClientId,
        @Schema(description = "租户编码") String tenantId,
        @Schema(description = "客户端 Client ID") String clientId,
        @Schema(description = "客户端名称") String clientName,
        @Schema(description = "授权主体用户名") String principalName,
        @Schema(description = "已授权的作用域列表") List<String> authorities,
        @Schema(description = "最近授权时间") Instant lastGrantedAt,
        @Schema(description = "最近撤销时间") Instant lastRevokedAt,
        @Schema(description = "关联授权审计事件数") long auditEventCount
) {
}
