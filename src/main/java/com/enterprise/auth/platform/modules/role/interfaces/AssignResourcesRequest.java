package com.enterprise.auth.platform.modules.role.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

@Schema(description = "角色资源分配请求")
public record AssignResourcesRequest(
        @Schema(description = "资源 ID 集合") @NotNull Set<Long> resourceIds
) {
}
