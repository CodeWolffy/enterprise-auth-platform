package com.enterprise.auth.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import com.enterprise.auth.platform.auth.dto.SliderCaptchaResponse;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CaptchaServiceRedisTest {

    private CaptchaService createService(TianaiCaptchaService tianaiCaptchaService) {
        return new CaptchaService(tianaiCaptchaService, new ObjectMapper());
    }

    @Test
    void createShouldReturnSliderCaptchaPayloadFromTianai() {
        TianaiCaptchaService tianaiCaptchaService = mock(TianaiCaptchaService.class);
        CaptchaService captchaService = createService(tianaiCaptchaService);
        when(tianaiCaptchaService.createSliderCaptcha()).thenReturn(new SliderCaptchaResponse(
            "captcha-ut",
            "background-base64",
            "slider-base64",
            320,
            180,
            64,
            180,
            "SLIDER"
        ));

        CaptchaService.CaptchaChallenge challenge = captchaService.create();

        verify(tianaiCaptchaService).createSliderCaptcha();
        assertThat(challenge.captchaId()).isEqualTo("captcha-ut");
        assertThat(challenge.backgroundImage()).isEqualTo("background-base64");
        assertThat(challenge.sliderImage()).isEqualTo("slider-base64");
    }

    @Test
    void verifyShouldDeserializeTrackAndDelegateToTianai() {
        TianaiCaptchaService tianaiCaptchaService = mock(TianaiCaptchaService.class);
        CaptchaService captchaService = createService(tianaiCaptchaService);

        captchaService.verify(
            "captcha-ut",
            """
            {
              "bgImageWidth": 320,
              "bgImageHeight": 180,
              "templateImageWidth": 64,
              "templateImageHeight": 180,
              "startTime": 1000,
              "stopTime": 1600,
              "left": 92,
              "top": 0,
              "trackList": [
                { "x": 0, "y": 0, "t": 0, "type": "MOVE" },
                { "x": 92, "y": 0, "t": 600, "type": "MOVE" }
              ]
            }
            """
        );

        verify(tianaiCaptchaService).validate(argThat(request -> {
            ImageCaptchaTrack track = request.track();
            return "captcha-ut".equals(request.captchaId())
                && track != null
                && Integer.valueOf(320).equals(track.getBgImageWidth())
                && Integer.valueOf(92).equals(track.getLeft())
                && track.getTrackList() != null
                && track.getTrackList().size() == 2;
        }));
    }

    @Test
    void verifyShouldRejectInvalidCaptchaPayload() {
        TianaiCaptchaService tianaiCaptchaService = mock(TianaiCaptchaService.class);
        CaptchaService captchaService = createService(tianaiCaptchaService);

        assertThatThrownBy(() -> captchaService.verify("captcha-ut", "{invalid-json}"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("验证码校验失败");
    }

    @Test
    void verifyShouldRejectBlankParameters() {
        TianaiCaptchaService tianaiCaptchaService = mock(TianaiCaptchaService.class);
        CaptchaService captchaService = createService(tianaiCaptchaService);

        assertThatThrownBy(() -> captchaService.verify("", " "))
            .isInstanceOf(BusinessException.class)
            .hasMessage("验证码参数不能为空");
    }

    @Test
    void validateShouldPassWhenSecondaryVerificationSucceeds() {
        TianaiCaptchaService tianaiCaptchaService = mock(TianaiCaptchaService.class);
        CaptchaService captchaService = createService(tianaiCaptchaService);
        when(tianaiCaptchaService.secondaryVerification("captcha-ut")).thenReturn(true);

        captchaService.validate("captcha-ut", "any-data");

        verify(tianaiCaptchaService).secondaryVerification("captcha-ut");
    }

    @Test
    void validateShouldRejectWhenSecondaryVerificationFails() {
        TianaiCaptchaService tianaiCaptchaService = mock(TianaiCaptchaService.class);
        CaptchaService captchaService = createService(tianaiCaptchaService);
        when(tianaiCaptchaService.secondaryVerification("captcha-ut")).thenReturn(false);

        assertThatThrownBy(() -> captchaService.validate("captcha-ut", "any-data"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("验证码未通过校验");
    }
}
