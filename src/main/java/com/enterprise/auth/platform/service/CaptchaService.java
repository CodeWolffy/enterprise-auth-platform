package com.enterprise.auth.platform.service;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.spring.plugins.secondary.SecondaryVerificationApplication;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import com.enterprise.auth.platform.dto.req.CaptchaVerifyRequest;
import com.enterprise.auth.platform.dto.resp.SliderCaptchaResponse;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {

    private final ImageCaptchaApplication imageCaptchaApplication;
    private final ObjectMapper objectMapper;

    public CaptchaService(ImageCaptchaApplication imageCaptchaApplication, ObjectMapper objectMapper) {
        this.imageCaptchaApplication = imageCaptchaApplication;
        this.objectMapper = objectMapper;
    }

    public CaptchaChallenge create() {
        SliderCaptchaResponse sliderCaptcha = createSliderCaptcha();
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
            validateTrack(new CaptchaVerifyRequest(captchaId, null, track));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("验证码校验失败");
        }
    }

    public void secondaryVerify(String captchaId) {
        if (captchaId == null || captchaId.isBlank()) {
            throw new BusinessException("验证码参数不能为空");
        }
        if (imageCaptchaApplication instanceof SecondaryVerificationApplication secondaryApp) {
            if (secondaryApp.secondaryVerification(captchaId)) {
                return;
            }
        }
        throw new BusinessException("验证码未通过校验");
    }

    private SliderCaptchaResponse createSliderCaptcha() {
        cloud.tianai.captcha.common.response.ApiResponse<ImageCaptchaVO> response =
            imageCaptchaApplication.generateCaptcha(CaptchaTypeConstant.SLIDER);
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("验证码生成失败");
        }
        ImageCaptchaVO vo = response.getData();
        if (vo == null) {
            throw new IllegalStateException("验证码数据为空");
        }
        return new SliderCaptchaResponse(
            vo.getId(),
            vo.getBackgroundImage(),
            vo.getTemplateImage(),
            vo.getBackgroundImageWidth(),
            vo.getBackgroundImageHeight(),
            vo.getTemplateImageWidth(),
            vo.getTemplateImageHeight(),
            vo.getType()
        );
    }

    private void validateTrack(CaptchaVerifyRequest request) {
        if (request == null || request.captchaId() == null || request.captchaId().isBlank() || request.track() == null) {
            throw new BusinessException("验证码参数不能为空");
        }
        try {
            cloud.tianai.captcha.common.response.ApiResponse<?> response =
                imageCaptchaApplication.matching(request.captchaId(), request.track());
            if (response == null || !response.isSuccess()) {
                String msg = response != null ? response.getMsg() : null;
                throw new BusinessException(msg == null || msg.isBlank() ? "验证码校验失败" : msg);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("验证码校验失败");
        }
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