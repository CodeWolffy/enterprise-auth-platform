package com.enterprise.auth.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "OAuth2 作用域维护请求")
public record OauthScopeCrudRequest(
        @Schema(description = "作用域编码") @NotBlank String scopeCode,
        @Schema(description = "作用域名称") @NotBlank String scopeName,
        @Schema(description = "作用域说明") String scopeDesc,
        @Schema(description = "作用域类型") String scopeType,
        @Schema(description = "是否默认选中") Boolean defaultSelected,
        @Schema(description = "是否在同意页展示") Boolean visibleInConsent,
        @Schema(description = "排序值") Integer sortOrder,
        @Schema(description = "是否启用") Boolean enabled
) {
}
