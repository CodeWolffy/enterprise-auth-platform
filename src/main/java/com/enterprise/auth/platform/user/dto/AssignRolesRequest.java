package com.enterprise.auth.platform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

@Schema(description = "用户角色分配请求")
public record AssignRolesRequest(
        @Schema(description = "角色编码集合", requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty Set<@NotBlank String> roleCodes
) {
}
