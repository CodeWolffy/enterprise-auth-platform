package com.enterprise.auth.platform.auth.service;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.spring.plugins.secondary.SecondaryVerificationApplication;
import com.enterprise.auth.platform.auth.dto.CaptchaVerificationRequest;
import com.enterprise.auth.platform.auth.dto.SliderCaptchaResponse;
import com.enterprise.auth.platform.common.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class TianaiCaptchaService {

    private final ImageCaptchaApplication imageCaptchaApplication;

    public TianaiCaptchaService(ImageCaptchaApplication imageCaptchaApplication) {
        this.imageCaptchaApplication = imageCaptchaApplication;
    }

    public SliderCaptchaResponse createSliderCaptcha() {
        ApiResponse<ImageCaptchaVO> response = imageCaptchaApplication.generateCaptcha(CaptchaTypeConstant.SLIDER);
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

    public void validate(CaptchaVerificationRequest request) {
        if (request == null || request.captchaId() == null || request.captchaId().isBlank() || request.track() == null) {
            throw new BusinessException("验证码参数不能为空");
        }
        try {
            ApiResponse<?> response = imageCaptchaApplication.matching(request.captchaId(), request.track());
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

    public boolean secondaryVerification(String captchaId) {
        if (imageCaptchaApplication instanceof SecondaryVerificationApplication secondaryApp) {
            return secondaryApp.secondaryVerification(captchaId);
        }
        return false;
    }
}
