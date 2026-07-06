package com.enterprise.auth.platform.modules.auth.infrastructure;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.impl.LocalMemoryResourceStore;
import cloud.tianai.captcha.validator.ImageCaptchaValidator;
import cloud.tianai.captcha.validator.impl.BasicCaptchaTrackValidator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 验证码资源与校验器配置。
 *
 * <p>说明：
 * <ul>
 *   <li>背景图同时注册给 {@link CaptchaTypeConstant#SLIDER 滑块} 与
 *       {@link CaptchaTypeConstant#WORD_IMAGE_CLICK 文字点选}，
 *       使两种类型都能复用同一套图库；文字点选所需字体(SIMSUN.TTC)由 tianai 默认资源自动注册。</li>
 *   <li>校验器用 {@link BasicCaptchaTrackValidator} 覆盖默认的 SimpleImageCaptchaValidator，
 *       在"落点容差"之外额外做行为轨迹检测(滑动耗时、轨迹点数、起点偏移、匀速判定、加速度前快后慢等)，
 *       使其成为真正的"行为验证码"而非单纯的"位置验证码"。</li>
 * </ul>
 */
@Configuration
public class CaptchaResourceConfiguration {

    private static final String[] BACKGROUND_IMAGES = {
        "META-INF/cut-image/resource/1.png",
        "META-INF/cut-image/resource/2.png",
        "META-INF/cut-image/resource/3.png"
    };

    @Bean
    public ResourceStore captchaResourceStore() {
        LocalMemoryResourceStore resourceStore = new LocalMemoryResourceStore();
        for (String path : BACKGROUND_IMAGES) {
            addIfExists(resourceStore, path);
        }
        return resourceStore;
    }

    /**
     * 覆盖 tianai starter 的默认校验器(@ConditionalOnMissingBean)，启用行为轨迹校验。
     * 对滑块类做完整行为检测，对点选类回退为位置校验(tianai 当前版本点选行为轨迹尚未实现)。
     */
    @Bean
    public ImageCaptchaValidator imageCaptchaValidator() {
        return new BasicCaptchaTrackValidator();
    }

    private void addIfExists(LocalMemoryResourceStore resourceStore, String path) {
        if (new ClassPathResource(path).exists()) {
            // 滑块拼图
            resourceStore.addResource(CaptchaTypeConstant.SLIDER, new Resource("classpath", path, "default"));
            // 文字点选(同一张底图用于绘制待点选文字)
            resourceStore.addResource(CaptchaTypeConstant.WORD_IMAGE_CLICK, new Resource("classpath", path, "default"));
        }
    }
}
