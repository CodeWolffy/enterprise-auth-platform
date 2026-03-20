package com.enterprise.auth.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "修改 OAuth2 客户端请求")
public record UpdateOauthClientRequest(
        @Schema(description = "客户端名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "客户端名称不能为空")
        String clientName,
        @Schema(description = "新的客户端密钥，为空表示沿用原密钥")
        String clientSecret,
        @Schema(description = "重定向地址列表", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "至少需要一个重定向地址")
        List<String> redirectUris,
        @Schema(description = "作用域列表", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "至少需要一个作用域")
        List<String> scopes,
        @Schema(description = "授权类型列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"authorization_code\",\"refresh_token\"]")
        @NotEmpty(message = "至少需要一个授权类型")
        List<String> grantTypes,
        @Schema(description = "是否要求 PKCE")
        Boolean requirePkce,
        @Schema(description = "是否要求授权确认")
        Boolean requireConsent,
        @Schema(description = "是否公共客户端，公共客户端走 PKCE 且不保存密钥")
        Boolean publicClient
) {
}
