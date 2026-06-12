package com.enterprise.auth.platform.infrastructure.redis;

import com.enterprise.auth.platform.common.cache.CacheNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisCacheConfig implements CachingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);


    @SuppressWarnings("deprecation")
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(
            AppCacheProperties cacheProperties,
            ObjectMapper objectMapper
    ) {
        ObjectMapper cacheObjectMapper = objectMapper.copy();
        cacheObjectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        cacheObjectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.EVERYTHING
        );
        cacheObjectMapper.addMixIn(
                com.enterprise.auth.platform.modules.user.application.AuthenticationUser.class,
                AuthenticationUserTypeMixin.class
        );
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(cacheObjectMapper);
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        RedisCacheConfiguration defaultConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(cacheProperties.resolvedDefaultTtl())
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> cacheProperties.resolvedNamespacePrefix() + cacheName + "::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
        return defaultConfiguration;
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(
            AppCacheProperties cacheProperties,
            RedisCacheConfiguration defaultRedisCacheConfiguration
    ) {
        return builder -> builder
                .withCacheConfiguration(CacheNames.AUTH_PRINCIPAL, defaultRedisCacheConfiguration.entryTtl(cacheProperties.resolvedAuthPrincipalTtl()))
                .withCacheConfiguration(CacheNames.REGISTRATION_POLICY, defaultRedisCacheConfiguration.entryTtl(cacheProperties.resolvedRegistrationPolicyTtl()))
                .withCacheConfiguration(CacheNames.SYSTEM_DICTS, defaultRedisCacheConfiguration.entryTtl(cacheProperties.resolvedSystemDictsTtl()))
                .withCacheConfiguration(CacheNames.SYSTEM_CONFIGS, defaultRedisCacheConfiguration.entryTtl(cacheProperties.resolvedSystemConfigsTtl()))
                .withCacheConfiguration(CacheNames.SYSTEM_NOTICES, defaultRedisCacheConfiguration.entryTtl(cacheProperties.resolvedSystemNoticesTtl()))
                .withCacheConfiguration(CacheNames.SYSTEM_CATEGORIES_ALL, defaultRedisCacheConfiguration.entryTtl(cacheProperties.resolvedSystemCategoriesAllTtl()))
                .withCacheConfiguration(CacheNames.SYSTEM_CATEGORIES_TARGET, defaultRedisCacheConfiguration.entryTtl(cacheProperties.resolvedSystemCategoriesTargetTtl()))
                .withCacheConfiguration(CacheNames.MENU_TEMPLATE, defaultRedisCacheConfiguration.entryTtl(cacheProperties.resolvedMenuTemplateTtl()));
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("缓存获取失败，回退到数据库。cache={}, key={}, error={}",
                        cache == null ? "unknown" : cache.getName(),
                        key,
                        exception.getMessage());
                if (cache != null && key != null) {
                    try {
                        cache.evict(key);
                    } catch (RuntimeException evictEx) {
                        log.warn("缓存坏键清理失败。cache={}, key={}, error={}",
                                cache.getName(),
                                key,
                                evictEx.getMessage());
                    }
                }
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("缓存写入失败。cache={}, key={}, error={}",
                        cache == null ? "unknown" : cache.getName(),
                        key,
                        exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("缓存删除失败。cache={}, key={}, error={}",
                        cache == null ? "unknown" : cache.getName(),
                        key,
                        exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("缓存清空失败。cache={}, error={}",
                        cache == null ? "unknown" : cache.getName(),
                        exception.getMessage());
            }
        };
    }
}