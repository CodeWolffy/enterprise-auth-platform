package com.enterprise.auth.platform.role.dto;

import com.enterprise.auth.platform.common.model.DataScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "修改角色请求")
public record UpdateRoleRequest(
        @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String roleName,
        @Schema(description = "角色描述") String roleDesc,
        @Schema(description = "数据权限范围", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull DataScopeType dataScopeType
) {
}
