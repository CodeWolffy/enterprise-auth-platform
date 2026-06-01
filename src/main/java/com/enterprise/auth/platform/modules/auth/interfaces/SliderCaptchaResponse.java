package com.enterprise.auth.platform.modules.auth.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "滑块验证码响应")
public record SliderCaptchaResponse(
        @Schema(description = "验证码ID") String captchaId,
        @Schema(description = "背景图(Base64)") String backgroundImage,
        @Schema(description = "滑块图(Base64)") String sliderImage,
        @Schema(description = "背景图宽度") Integer backgroundImageWidth,
        @Schema(description = "背景图高度") Integer backgroundImageHeight,
        @Schema(description = "滑块图宽度") Integer sliderImageWidth,
        @Schema(description = "滑块图高度") Integer sliderImageHeight,
        @Schema(description = "滑块类型") String type
) {
}
