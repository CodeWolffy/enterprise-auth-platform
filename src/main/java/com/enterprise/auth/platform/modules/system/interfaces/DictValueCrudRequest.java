package com.enterprise.auth.platform.modules.system.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "字典值新增或修改请求")
public record DictValueCrudRequest(
        @Schema(description = "字典标签", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String dictLabel,
        @Schema(description = "字典键值", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String dictValue,
        @Schema(description = "排序") Integer sort,
        @Schema(description = "回显样式") String showClass,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "备注") String remarks
) {
}