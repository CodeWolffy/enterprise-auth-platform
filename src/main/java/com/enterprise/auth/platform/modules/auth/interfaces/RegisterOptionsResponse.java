package com.enterprise.auth.platform.modules.auth.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "注册公共配置")
public record RegisterOptionsResponse(
        @Schema(description = "默认租户编码") String defaultTenantId,
        @Schema(description = "默认角色编码集合") List<String> defaultRoleCodes
) {
}