package com.enterprise.auth.platform.auth.service;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import com.enterprise.auth.platform.auth.dto.CaptchaVerificationRequest;
import com.enterprise.auth.platform.auth.dto.SliderCaptchaResponse;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {

    private final TianaiCaptchaService tianaiCaptchaService;
    private final ObjectMapper objectMapper;

    public CaptchaService(TianaiCaptchaService tianaiCaptchaService, ObjectMapper objectMapper) {
        this.tianaiCaptchaService = tianaiCaptchaService;
        this.objectMapper = objectMapper;
    }

    public CaptchaChallenge create() {
        SliderCaptchaResponse sliderCaptcha = tianaiCaptchaService.createSliderCaptcha();
        return new CaptchaChallenge(
            sliderCaptcha.captchaId(),
            sliderCaptcha.backgroundImage(),
            sliderCaptcha.sliderImage(),
            sliderCaptcha.backgroundImageWidth(),
            sliderCaptcha.backgroundImageHeight(),
            sliderCaptcha.sliderImageWidth(),
            sliderCaptcha.sliderImageHeight()
        );
    }

    public void verify(String captchaId, String captchaData) {
        if (captchaId == null || captchaId.isBlank() || captchaData == null || captchaData.isBlank()) {
            throw new BusinessException("验证码参数不能为空");
        }
        try {
            ImageCaptchaTrack track = objectMapper.readValue(captchaData, ImageCaptchaTrack.class);
            tianaiCaptchaService.validate(new CaptchaVerificationRequest(captchaId, track));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("验证码校验失败");
        }
    }

    public void validate(String captchaId, String captchaData) {
        if (captchaId == null || captchaId.isBlank()) {
            throw new BusinessException("验证码参数不能为空");
        }
        if (tianaiCaptchaService.secondaryVerification(captchaId)) {
            return;
        }
        throw new BusinessException("验证码未通过校验");
    }

    public record CaptchaChallenge(
        String captchaId,
        String backgroundImage,
        String sliderImage,
        Integer backgroundImageWidth,
        Integer backgroundImageHeight,
        Integer sliderImageWidth,
        Integer sliderImageHeight
    ) {
    }
}
