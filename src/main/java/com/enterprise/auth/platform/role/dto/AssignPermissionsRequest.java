package com.enterprise.auth.platform.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

@Schema(description = "角色权限分配请求")
public record AssignPermissionsRequest(
        @Schema(description = "权限编码集合", requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty Set<@NotBlank String> permissionCodes
) {
}
