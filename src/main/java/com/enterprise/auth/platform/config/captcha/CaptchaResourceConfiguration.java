package com.enterprise.auth.platform.config.captcha;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.impl.LocalMemoryResourceStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaptchaResourceConfiguration {

    @Bean
    public ResourceStore captchaResourceStore() {
        LocalMemoryResourceStore resourceStore = new LocalMemoryResourceStore();
        addIfExists(resourceStore, "META-INF/cut-image/resource/1.png");
        addIfExists(resourceStore, "META-INF/cut-image/resource/2.png");
        addIfExists(resourceStore, "META-INF/cut-image/resource/3.png");
        return resourceStore;
    }

    private void addIfExists(LocalMemoryResourceStore resourceStore, String path) {
        if (new ClassPathResource(path).exists()) {
            resourceStore.addResource(CaptchaTypeConstant.SLIDER, new Resource("classpath", path, "default"));
        }
    }
}