package com.enterprise.auth.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "滑块验证码校验请求")
public record CaptchaVerifyRequest(
    @Schema(description = "验证码ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank String captchaId,
    @Schema(description = "验证码校验数据（JSON 格式的滑动轨迹）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank String captchaCode
) {}
