package com.enterprise.auth.platform;

import com.enterprise.auth.platform.config.AuthorizationServerProperties;
import com.enterprise.auth.platform.config.FeatureToggleProperties;
import com.enterprise.auth.platform.config.FrontendProperties;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.config.SecurityRedisProperties;
import com.enterprise.auth.platform.config.SecurityProperties;
import com.enterprise.auth.platform.tenant.TenantProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = {
        AuthorizationServerProperties.class,
        SecurityProperties.class,
        SecurityRedisProperties.class,
        FeatureToggleProperties.class,
        FrontendProperties.class,
        PersistenceProperties.class,
        TenantProperties.class
})
public class EnterpriseAuthPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseAuthPlatformApplication.class, args);
    }
}
