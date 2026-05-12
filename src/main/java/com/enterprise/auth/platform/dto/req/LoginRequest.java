package com.enterprise.auth.platform.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "登录请求")
public record LoginRequest(
        @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String username,
        @Schema(description = "登录密码", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String password,
        @Schema(description = "验证码ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String captchaId,
        @Schema(description = "验证码内容", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String captchaCode,
        @Schema(description = "租户编码，为空时从请求头解析") String tenantId,
        @Schema(description = "登录设备标识") String device
) {
}
