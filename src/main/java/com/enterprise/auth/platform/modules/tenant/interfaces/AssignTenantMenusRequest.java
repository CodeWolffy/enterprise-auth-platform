package com.enterprise.auth.platform.modules.tenant.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

@Schema(description = "租户菜单分配请求")
public record AssignTenantMenusRequest(
        @Schema(description = "菜单ID集合", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty Set<Long> menuIds
) {
}