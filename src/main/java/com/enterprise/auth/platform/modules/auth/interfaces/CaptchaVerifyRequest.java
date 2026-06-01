package com.enterprise.auth.platform.modules.auth.interfaces;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "滑块验证码校验请求")
public record CaptchaVerifyRequest(
    @Schema(description = "验证码ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank String captchaId,
    @Schema(description = "验证码校验数据（JSON 格式的滑动轨迹）")
    String captchaCode,
    @Schema(description = "用户滑动轨迹数据（反序列化后）")
    ImageCaptchaTrack track
) {}