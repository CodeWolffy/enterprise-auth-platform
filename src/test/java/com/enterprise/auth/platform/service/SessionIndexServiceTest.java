package com.enterprise.auth.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

class SessionIndexServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void registerShouldPersistActiveTenantWithLoginTenant() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        SessionIndexService service = new SessionIndexService(redisTemplate, securityProperties());

        service.register("token-1", 100L, "admin", "platform", "127.0.0.1", "内网IP", "browser", 1000L, 2000L);

        verify(hashOps).putAll(eq("eap:test:v1:session:meta:token-1"), argThat(meta -> {
            assertThat(meta).isInstanceOf(Map.class);
            return "platform".equals(meta.get("tenantId"))
                    && "platform".equals(meta.get("activeTenantId"));
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateActiveTenantShouldUseLuaScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any())).thenReturn(1L);
        SessionIndexService service = new SessionIndexService(redisTemplate, securityProperties());

        service.updateActiveTenant("token-1", "tenant-a");

        verify(redisTemplate).execute(any(RedisScript.class), any(List.class), eq("tenant-a"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void countVisibleShouldFilterByTenantAndVisibleUsers() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(zSetOps.zCard("eap:test:v1:session:index:tenant:tenant-a")).thenReturn(3L);
        when(zSetOps.reverseRange("eap:test:v1:session:index:tenant:tenant-a", 0, 199L))
                .thenReturn(Set.of("token-1", "token-2", "token-3"));
        when(redisTemplate.executePipelined(any(SessionCallback.class))).thenReturn(List.of(
                Map.of(
                        "userId", "100",
                        "username", "admin",
                        "tenantId", "tenant-a",
                        "activeTenantId", "tenant-a"
                ),
                Map.of(
                        "userId", "200",
                        "username", "visible",
                        "tenantId", "tenant-a",
                        "activeTenantId", "tenant-a"
                ),
                Map.of(
                        "userId", "300",
                        "username", "other",
                        "tenantId", "tenant-b",
                        "activeTenantId", "tenant-b"
                )
        ));
        SessionIndexService service = new SessionIndexService(redisTemplate, securityProperties());

        Optional<Long> count = service.countVisible("tenant-a", false, Optional.of(Set.of(100L)));

        assertThat(count).contains(1L);
    }

    private SecurityProperties securityProperties() {
        return new SecurityProperties(
                Duration.ofDays(7),
                Duration.ofMinutes(1),
                false,
                "Lax",
                new SecurityProperties.Redis(true, false, false, "eap:test:", "v1", Duration.ofSeconds(30))
        );
    }
}
