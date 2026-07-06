package com.enterprise.auth.platform.modules.auth.application;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.spring.autoconfiguration.SpringImageCaptchaProperties;
import cloud.tianai.captcha.spring.plugins.secondary.SecondaryVerificationApplication;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import com.enterprise.auth.platform.modules.auth.interfaces.CaptchaVerifyRequest;
import com.enterprise.auth.platform.modules.user.application.UserAuthenticationFacade;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CaptchaService {

    /** 同一账号在失败窗口内累计失败达到该阈值后，验证码由滑块升级为文字点选(打码成本更高)。 */
    private static final long ESCALATE_AFTER_FAILURES = 2;

    private final ImageCaptchaApplication imageCaptchaApplication;
    private final ObjectMapper objectMapper;
    private final SpringImageCaptchaProperties captchaProperties;
    private final LoginAttemptService loginAttemptService;
    private final UserAuthenticationFacade userAuthenticationFacade;

    public CaptchaService(ImageCaptchaApplication imageCaptchaApplication,
                          ObjectMapper objectMapper,
                          SpringImageCaptchaProperties captchaProperties,
                          LoginAttemptService loginAttemptService,
                          UserAuthenticationFacade userAuthenticationFacade) {
        this.imageCaptchaApplication = imageCaptchaApplication;
        this.objectMapper = objectMapper;
        this.captchaProperties = captchaProperties;
        this.loginAttemptService = loginAttemptService;
        this.userAuthenticationFacade = userAuthenticationFacade;
    }

    /** 默认滑块验证码(注册/重置等无风险升级场景使用)。 */
    public CaptchaChallenge create() {
        return create(CaptchaTypeConstant.SLIDER);
    }

    /**
     * 登录场景生成验证码：按账号近期失败情况决定类型——
     * 失败达到阈值升级为文字点选，否则使用滑块。
     */
    public CaptchaChallenge createForLogin(String tenantId, String username) {
        return create(resolveLoginType(tenantId, username));
    }

    public CaptchaChallenge create(String type) {
        ImageCaptchaVO vo = generate(type);
        return new CaptchaChallenge(
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

    private String resolveLoginType(String headerTenantId, String username) {
        long failures = loginAttemptService.currentFailures(resolveFailureTenantId(headerTenantId, username), username);
        return failures >= ESCALATE_AFTER_FAILURES
            ? CaptchaTypeConstant.WORD_IMAGE_CLICK
            : CaptchaTypeConstant.SLIDER;
    }

    /**
     * 解析失败计数所在的租户，须与 {@code LoginApplicationService} 记录侧一致：
     * 优先按用户名解析出的唯一真实租户，否则回退请求头租户。
     * 与登录侧不同，这里对冲突/无匹配一律回退而非抛异常(取码阶段不应因此失败)。
     */
    private String resolveFailureTenantId(String headerTenantId, String username) {
        if (username == null || username.isBlank()) {
            return headerTenantId;
        }
        try {
            List<String> matched = userAuthenticationFacade.activeTenantIdsByUsername(username);
            if (matched.size() == 1) {
                return matched.get(0);
            }
        } catch (Exception ignored) {
            // 取码阶段容错：解析失败时回退请求头租户
        }
        return headerTenantId;
    }

    public CaptchaVerificationToken verify(String captchaId, String captchaData) {
        if (captchaId == null || captchaId.isBlank() || captchaData == null || captchaData.isBlank()) {
            throw new BusinessException("验证码参数不能为空");
        }
        try {
            ImageCaptchaTrack track = objectMapper.readValue(captchaData, ImageCaptchaTrack.class);
            validateTrack(new CaptchaVerifyRequest(captchaId, null, track));
            return new CaptchaVerificationToken(captchaId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("验证码校验失败");
        }
    }

    public void secondaryVerify(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("验证码参数不能为空");
        }
        if (imageCaptchaApplication instanceof SecondaryVerificationApplication secondaryApp) {
            if (secondaryApp.secondaryVerification(token)) {
                return;
            }
        }
        throw new BusinessException("验证码未通过校验");
    }

    /**
     * 验证二次验证码token是否存在，但不消耗它。
     * 用于登录场景：先验证token有效性，密码验证失败时token仍可复用。
     */
    public boolean secondaryVerifyWithoutRemoval(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (imageCaptchaApplication instanceof SecondaryVerificationApplication secondaryApp) {
            try {
                var cacheStore = secondaryApp.getCacheStore();
                String keyPrefix = captchaProperties.getSecondary().getKeyPrefix();
                String key = keyPrefix + ":" + token;
                return cacheStore.getCache(key) != null;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private ImageCaptchaVO generate(String type) {
        cloud.tianai.captcha.common.response.ApiResponse<ImageCaptchaVO> response =
            imageCaptchaApplication.generateCaptcha(type);
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("验证码生成失败");
        }
        ImageCaptchaVO vo = response.getData();
        if (vo == null) {
            throw new IllegalStateException("验证码数据为空");
        }
        return vo;
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
        Integer sliderImageHeight,
        String type
    ) {
    }

    public record CaptchaVerificationToken(String token) {
    }
}