package com.enterprise.auth.platform.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;

@Schema(description = "更新租户能力覆盖请求")
public record UpdateTenantCapabilityOverridesRequest(
        @Schema(description = "能力覆盖项列表")
        @Valid
        List<CapabilityOverrideItem> overrides
) {

    @Schema(description = "能力覆盖项")
    public record CapabilityOverrideItem(
            @Schema(description = "能力编码", requiredMode = Schema.RequiredMode.REQUIRED)
            String capabilityCode,
            @Schema(description = "是否启用；为空表示继承套餐默认状态")
            Boolean enabled,
            @Schema(description = "能力说明覆盖")
            String capabilityDescOverride
    ) {
    }
}
