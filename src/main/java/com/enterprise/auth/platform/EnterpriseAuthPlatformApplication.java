package com.enterprise.auth.platform;

import com.enterprise.auth.platform.config.cache.AppCacheProperties;
import com.enterprise.auth.platform.config.feature.FeatureToggleProperties;
import com.enterprise.auth.platform.config.web.FrontendProperties;
import com.enterprise.auth.platform.config.feature.RegistrationProperties;
import com.enterprise.auth.platform.config.security.SecurityRedisProperties;
import com.enterprise.auth.platform.config.security.SecurityProperties;
import com.enterprise.auth.platform.tenant.TenantProperties;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = {
        SecurityProperties.class,
        AppCacheProperties.class,
        SecurityRedisProperties.class,
        FeatureToggleProperties.class,
        FrontendProperties.class,
        RegistrationProperties.class,
        TenantProperties.class
})
public class EnterpriseAuthPlatformApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(EnterpriseAuthPlatformApplication.class, args);
    }
}
