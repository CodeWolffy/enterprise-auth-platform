package com.enterprise.auth.platform.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.system.application.SystemViewModels;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;

class RedisCacheConfigTest {

    @Test
    void valueSerializerShouldPreservePageResultType() {
        RedisCacheConfig config = new RedisCacheConfig();
        RedisCacheConfiguration cacheConfiguration = config.redisCacheConfiguration(
                cacheProperties(),
                new ObjectMapper().findAndRegisterModules()
        );
        RedisSerializationContext.SerializationPair<Object> serializer = cacheConfiguration.getValueSerializationPair();
        PageResult<SampleView> source = PageResult.of(1, 1, 10, List.of(new SampleView("config-a")));

        ByteBuffer serialized = serializer.write(source);
        Object restored = serializer.read(serialized);

        assertThat(restored).isInstanceOf(PageResult.class);
        PageResult<?> page = (PageResult<?>) restored;
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.records()).singleElement().isEqualTo(new SampleView("config-a"));
    }

    @Test
    void valueSerializerShouldPreserveCategoryCacheStructures() {
        RedisCacheConfig config = new RedisCacheConfig();
        RedisCacheConfiguration cacheConfiguration = config.redisCacheConfiguration(
                cacheProperties(),
                new ObjectMapper().findAndRegisterModules()
        );
        RedisSerializationContext.SerializationPair<Object> serializer = cacheConfiguration.getValueSerializationPair();
        List<SystemViewModels.CategoryOption> categoryOptions = List.of(
                new SystemViewModels.CategoryOption("base", "基础配置", List.of("system.*"))
        );
        Map<String, List<SystemViewModels.CategoryOption>> categories = Map.of(
                "dict", categoryOptions,
                "config", List.of()
        );

        Object restoredOptions = serializer.read(serializer.write(categoryOptions));
        Object restoredCategories = serializer.read(serializer.write(categories));

        assertThat(restoredOptions).isEqualTo(categoryOptions);
        assertThat(restoredCategories).isEqualTo(categories);
    }

    private AppCacheProperties cacheProperties() {
        return new AppCacheProperties(
                "eap:cache:",
                "test-v",
                Duration.ofMinutes(10),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private record SampleView(String name) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}