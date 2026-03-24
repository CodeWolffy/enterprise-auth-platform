package com.enterprise.auth.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class RedisCacheConfig {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);


    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(
            AppCacheProperties cacheProperties
    ) {
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(cacheProperties.resolvedDefaultTtl())
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> cacheProperties.resolvedKeyPrefix() + cacheName + "::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(
            AppCacheProperties cacheProperties,
            RedisCacheConfiguration defaultRedisCacheConfiguration
    ) {
        return builder -> builder
                .withCacheConfiguration("auth:principal", defaultRedisCacheConfiguration.entryTtl(cacheProperties.resolvedAuthPrincipalTtl()))
                .withCacheConfiguration("system:dicts", defaultRedisCacheConfiguration.entryTtl(cacheProperties.resolvedSystemDictsTtl()))
                .withCacheConfiguration("system:configs", defaultRedisCacheConfiguration.entryTtl(cacheProperties.resolvedSystemConfigsTtl()))
                .withCacheConfiguration("system:categories:all", defaultRedisCacheConfiguration.entryTtl(cacheProperties.resolvedSystemCategoriesAllTtl()))
                .withCacheConfiguration("system:categories:target", defaultRedisCacheConfiguration.entryTtl(cacheProperties.resolvedSystemCategoriesTargetTtl()));
    }

    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache get failed, fallback to DB. cache={}, key={}, error={}",
                        cache == null ? "unknown" : cache.getName(),
                        key,
                        exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache put failed. cache={}, key={}, error={}",
                        cache == null ? "unknown" : cache.getName(),
                        key,
                        exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache evict failed. cache={}, key={}, error={}",
                        cache == null ? "unknown" : cache.getName(),
                        key,
                        exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache clear failed. cache={}, error={}",
                        cache == null ? "unknown" : cache.getName(),
                        exception.getMessage());
            }
        };
    }
}
