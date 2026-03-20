package com.enterprise.auth.platform.permission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "修改权限请求")
public record UpdatePermissionRequest(
        @Schema(description = "资源编码", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String resourceCode,
        @Schema(description = "动作编码", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String actionCode,
        @Schema(description = "作用域编码", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String scopeCode,
        @Schema(description = "权限名称") String permissionName,
        @Schema(description = "权限编码", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String permissionCode
) {
}
