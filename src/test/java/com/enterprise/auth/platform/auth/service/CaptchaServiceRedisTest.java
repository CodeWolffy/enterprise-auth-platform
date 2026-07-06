package com.enterprise.auth.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.spring.autoconfiguration.SpringImageCaptchaProperties;
import com.enterprise.auth.platform.modules.auth.application.CaptchaService;
import com.enterprise.auth.platform.modules.auth.application.LoginAttemptService;
import com.enterprise.auth.platform.modules.user.application.UserAuthenticationFacade;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CaptchaServiceRedisTest {

    private CaptchaService createService(ImageCaptchaApplication imageCaptchaApplication) {
        LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
        UserAuthenticationFacade userAuthenticationFacade = mock(UserAuthenticationFacade.class);
        return new CaptchaService(imageCaptchaApplication, new ObjectMapper(), new SpringImageCaptchaProperties(), loginAttemptService, userAuthenticationFacade);
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
        ApiResponse<ImageCaptchaVO> response = mock(ApiResponse.class);
        when(response.isSuccess()).thenReturn(true);
        when(response.getData()).thenReturn(vo);
        when(app.generateCaptcha(CaptchaTypeConstant.SLIDER)).thenReturn(response);

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
