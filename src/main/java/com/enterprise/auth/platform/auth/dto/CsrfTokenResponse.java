package com.enterprise.auth.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "CSRF token 响应")
public record CsrfTokenResponse(
        @Schema(description = "Header 名称") String headerName,
        @Schema(description = "参数名称") String parameterName,
        @Schema(description = "Token 值") String token
) {
}
