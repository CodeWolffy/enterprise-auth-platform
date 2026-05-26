package com.enterprise.auth.platform.infrastructure.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RedissonConfig {

    private static final Logger log = LoggerFactory.getLogger(RedissonConfig.class);

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "app.security.redis", name = "redisson-enabled", havingValue = "true")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        String host = StringUtils.hasText(redisProperties.getHost()) ? redisProperties.getHost() : "127.0.0.1";
        int port = redisProperties.getPort();

        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
            .setDatabase(redisProperties.getDatabase());

        if (StringUtils.hasText(redisProperties.getPassword())) {
            config.useSingleServer().setPassword(redisProperties.getPassword());
        }

        try {
            return Redisson.create(config);
        } catch (Exception ex) {
            log.warn("Redisson 初始化失败，已回退到本地模式: {}", ex.getMessage());
            return null;
        }
    }
}