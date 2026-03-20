package com.enterprise.auth.platform.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "字典新增或修改请求")
public record DictCrudRequest(
        @Schema(description = "字典类型", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String dictType,
        @Schema(description = "字典编码", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String dictCode,
        @Schema(description = "字典值", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String dictValue
) {
}
