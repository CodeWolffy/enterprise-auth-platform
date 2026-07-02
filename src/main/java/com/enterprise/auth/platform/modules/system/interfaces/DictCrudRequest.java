package com.enterprise.auth.platform.modules.system.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "字典新增或修改请求")
public record DictCrudRequest(
        @Schema(description = "字典类型", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String dictType,
        @Schema(description = "字典类型说明") String description,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "备注") String remarks
) {
}
