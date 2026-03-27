package com.enterprise.auth.platform;

import com.enterprise.auth.platform.config.AuthorizationServerProperties;
import com.enterprise.auth.platform.config.AppCacheProperties;
import com.enterprise.auth.platform.config.FeatureToggleProperties;
import com.enterprise.auth.platform.config.FrontendProperties;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.config.RegistrationProperties;
import com.enterprise.auth.platform.config.SecurityRedisProperties;
import com.enterprise.auth.platform.config.SecurityProperties;
import com.enterprise.auth.platform.tenant.TenantProperties;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = {
        AuthorizationServerProperties.class,
        SecurityProperties.class,
        AppCacheProperties.class,
        SecurityRedisProperties.class,
        FeatureToggleProperties.class,
        FrontendProperties.class,
        PersistenceProperties.class,
        RegistrationProperties.class,
        TenantProperties.class
})
public class EnterpriseAuthPlatformApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(EnterpriseAuthPlatformApplication.class, args);
    }
}
