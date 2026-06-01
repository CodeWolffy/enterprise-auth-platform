package com.enterprise.auth.platform.modules.tenant.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "租户能力维护请求")
public record TenantCapabilityCrudRequest(
        @Schema(description = "能力编码") @NotBlank String capabilityCode,
        @Schema(description = "能力名称") @NotBlank String capabilityName,
        @Schema(description = "能力说明") String capabilityDesc,
        @Schema(description = "排序值") Integer sortOrder,
        @Schema(description = "是否启用") Boolean enabled
) {
}
