package com.enterprise.auth.platform.auth.dto;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "滑块验证码校验请求")
public record CaptchaVerificationRequest(
        @Schema(description = "验证码ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String captchaId,
        @Schema(description = "用户滑动轨迹数据") ImageCaptchaTrack track
) {
}
