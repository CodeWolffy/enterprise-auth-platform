package com.enterprise.auth.platform.infrastructure.redis;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SaTokenStorageConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.security.redis", name = "session-enabled", havingValue = "false")
    public SaTokenDao localSaTokenDao() {
        return new SaTokenDaoDefaultImpl();
    }
}