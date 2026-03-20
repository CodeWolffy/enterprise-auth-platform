package com.enterprise.auth.platform.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Schema(description = "新增租户请求")
public record CreateTenantRequest(
        @Schema(description = "租户编码", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String tenantId,
        @Schema(description = "租户名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String tenantName,
        @Schema(description = "是否平台级租户") boolean platformLevel,
        @Schema(description = "租户状态，1 启用，0 禁用") Integer tenantStatus,
        @Schema(description = "到期时间") LocalDateTime expireAt
) {
}
