package com.enterprise.auth.platform.modules.system.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "参数新增或修改请求")
public record ConfigCrudRequest(
        @Schema(description = "参数键", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String configKey,
        @Schema(description = "参数名称") String configName,
        @Schema(description = "参数值", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String configValue
) {
}
