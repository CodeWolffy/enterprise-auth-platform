package com.enterprise.auth.platform.modules.system.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "参数新增或修改请求")
public record ConfigCrudRequest(
        @Schema(description = "参数键", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String configKey,
        @Schema(description = "参数名称") String configName,
        @Schema(description = "参数值", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String configValue,
        @Schema(description = "参数类型：business 业务参数，system 系统参数") String configType,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "是否内置") Boolean builtin,
        @Schema(description = "备注") String remark
) {
    public ConfigCrudRequest(String configKey, String configName, String configValue) {
        this(configKey, configName, configValue, null, null, null, null);
    }
}
