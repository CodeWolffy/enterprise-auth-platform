package com.enterprise.auth.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.common.response.ApiResponse;
import com.enterprise.auth.platform.service.CaptchaService;
import com.enterprise.auth.platform.common.convention.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CaptchaServiceRedisTest {

    private CaptchaService createService(ImageCaptchaApplication imageCaptchaApplication) {
        return new CaptchaService(imageCaptchaApplication, new ObjectMapper());
    }

    @Test
    void createShouldReturnSliderCaptchaPayloadFromTianai() {
        ImageCaptchaApplication app = mock(ImageCaptchaApplication.class);
        CaptchaService captchaService = createService(app);
        ImageCaptchaVO vo = new ImageCaptchaVO();
        vo.setId("captcha-ut");
        vo.setBackgroundImage("background-base64");
        vo.setTemplateImage("slider-base64");
        vo.setBackgroundImageWidth(320);
        vo.setBackgroundImageHeight(180);
        vo.setTemplateImageWidth(64);
        vo.setTemplateImageHeight(180);
        vo.setType("SLIDER");
        when(app.generateCaptcha(CaptchaTypeConstant.SLIDER)).thenReturn(mock(cloud.tianai.captcha.common.response.ApiResponse.class));

        CaptchaService.CaptchaChallenge challenge = captchaService.create();

        assertThat(challenge.captchaId()).isEqualTo("captcha-ut");
        assertThat(challenge.backgroundImage()).isEqualTo("background-base64");
        assertThat(challenge.sliderImage()).isEqualTo("slider-base64");
    }

    @Test
    void verifyShouldRejectInvalidCaptchaPayload() {
        ImageCaptchaApplication app = mock(ImageCaptchaApplication.class);
        CaptchaService captchaService = createService(app);

        assertThatThrownBy(() -> captchaService.verify("captcha-ut", "{invalid-json}"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("验证码校验失败");
    }

    @Test
    void verifyShouldRejectBlankParameters() {
        ImageCaptchaApplication app = mock(ImageCaptchaApplication.class);
        CaptchaService captchaService = createService(app);

        assertThatThrownBy(() -> captchaService.verify("", " "))
            .isInstanceOf(BusinessException.class)
            .hasMessage("验证码参数不能为空");
    }
}