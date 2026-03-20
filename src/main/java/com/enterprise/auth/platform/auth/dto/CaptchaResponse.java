package com.enterprise.auth.platform.auth.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "验证码响应")
public record CaptchaResponse(
        @Schema(description = "验证码ID") String captchaId,
        @Schema(description = "过期时间") Instant expiresAt,
        @Schema(description = "验证码预览，仅开发环境展示") String previewCode
) {
}
