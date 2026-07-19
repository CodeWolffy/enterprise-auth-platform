package com.enterprise.auth.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

class SessionIndexServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void registerShouldPersistActiveTenantWithLoginTenant() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        SessionIndexService service = new SessionIndexService(redisTemplate, securityProperties());

        service.register("token-1", 100L, "admin", "platform", "127.0.0.1", "内网IP", "browser", 1000L, 2000L);

        verify(hashOps).putAll(eq("eap:test:v1:session:meta:token-1"), argThat(meta -> {
            assertThat(meta).isInstanceOf(Map.class);
            String managementId = String.valueOf(meta.get("managementId"));
            return "platform".equals(meta.get("tenantId"))
                    && "platform".equals(meta.get("activeTenantId"))
                    && managementId.equals(meta.get("sessionId"))
                    && !"token-1".equals(managementId);
        }));
    }

    @Test
    void localIndexShouldExposeRandomManagementIdAndResolveItToToken() {
        SessionIndexService service = new SessionIndexService(mock(StringRedisTemplate.class), localSecurityProperties());

        service.register("secret-token", 100L, "admin", "platform", "127.0.0.1", "内网IP", "browser", 1000L, 2000L);

        String managementId = service.managementId("secret-token").orElseThrow();
        assertThat(managementId).isNotEqualTo("secret-token");
        assertThat(managementId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(service.resolveToken(managementId)).contains("secret-token");
        assertThat(service.resolveToken("secret-token")).isEmpty();
        assertThat(service.pageUser(100L, 0, 10).orElseThrow().records().get(0).response().sessionId())
                .isEqualTo(managementId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void readingLegacyMetaShouldBackfillManagementIdWithoutReturningToken() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(hashOps.putIfAbsent(any(), eq("managementId"), any())).thenReturn(true);
        when(zSetOps.zCard("eap:test:v1:session:index:user:100")).thenReturn(1L);
        when(zSetOps.reverseRange("eap:test:v1:session:index:user:100", 0, 9L)).thenReturn(Set.of("legacy-token"));
        when(redisTemplate.executePipelined(any(SessionCallback.class))).thenReturn(List.of(Map.of(
                "userId", "100",
                "username", "admin",
                "tenantId", "platform",
                "activeTenantId", "platform"
        )));
        SessionIndexService service = new SessionIndexService(redisTemplate, securityProperties());

        SessionIndexService.IndexedSession indexed = service.pageUser(100L, 0, 10)
                .orElseThrow().records().get(0);

        assertThat(indexed.response().sessionId()).isNotBlank().isNotEqualTo("legacy-token");
        assertThat(indexed.token()).isEqualTo("legacy-token");
        verify(hashOps).putIfAbsent(eq("eap:test:v1:session:meta:legacy-token"), eq("managementId"), any());
        verify(valueOps).set(
                eq("eap:test:v1:session:management:" + indexed.response().sessionId()),
                eq("legacy-token"),
                eq(Duration.ofDays(7).plusHours(1))
        );
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
        when(zSetOps.reverseRange("eap:test:v1:session:index:tenant:tenant-a", 0, 2L))
                .thenReturn(Set.of("token-1", "token-2", "token-3"));
        when(redisTemplate.executePipelined(any(SessionCallback.class))).thenReturn(List.of(
                Map.of(
                        "userId", "100",
                        "managementId", "management-1",
                        "username", "admin",
                        "tenantId", "tenant-a",
                        "activeTenantId", "tenant-a"
                ),
                Map.of(
                        "userId", "200",
                        "managementId", "management-2",
                        "username", "visible",
                        "tenantId", "tenant-a",
                        "activeTenantId", "tenant-a"
                ),
                Map.of(
                        "userId", "300",
                        "managementId", "management-3",
                        "username", "other",
                        "tenantId", "tenant-b",
                        "activeTenantId", "tenant-b"
                )
        ));
        SessionIndexService service = new SessionIndexService(redisTemplate, securityProperties());

        Optional<Long> count = service.countVisible("tenant-a", false, Optional.of(Set.of(100L)));

        assertThat(count).contains(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageVisibleShouldScanThreeBatchesButOnlyReturnRequestedDeepPage() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        String tenantKey = "eap:test:v1:session:index:tenant:tenant-a";
        when(zSetOps.zCard(tenantKey)).thenReturn(450L);
        when(zSetOps.reverseRange(tenantKey, 0, 199)).thenReturn(tokens(0, 200));
        when(zSetOps.reverseRange(tenantKey, 200, 399)).thenReturn(tokens(200, 400));
        when(zSetOps.reverseRange(tenantKey, 400, 449)).thenReturn(tokens(400, 450));
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
                .thenReturn(metadata(0, 200), metadata(200, 400), metadata(400, 450));
        SessionIndexService service = new SessionIndexService(redisTemplate, securityProperties());

        SessionIndexService.Page result = service.pageVisible(
                "tenant-a", Optional.of(Set.of(1L)), 4, 10).orElseThrow();

        assertThat(result.total()).isEqualTo(150);
        assertThat(result.records()).hasSize(10);
        assertThat(result.records()).extracting(SessionIndexService.IndexedSession::token)
                .containsExactly(
                        "token-120", "token-123", "token-126", "token-129", "token-132",
                        "token-135", "token-138", "token-141", "token-144", "token-147"
                );
        verify(zSetOps, times(1)).zCard(tenantKey);
        verify(zSetOps).reverseRange(tenantKey, 0, 199);
        verify(zSetOps).reverseRange(tenantKey, 200, 399);
        verify(zSetOps).reverseRange(tenantKey, 400, 449);
        verify(redisTemplate, times(3)).executePipelined(any(SessionCallback.class));
    }

    private Set<String> tokens(int startInclusive, int endExclusive) {
        return new LinkedHashSet<>(java.util.stream.IntStream.range(startInclusive, endExclusive)
                .mapToObj(index -> "token-" + index)
                .toList());
    }

    private List<Object> metadata(int startInclusive, int endExclusive) {
        return java.util.stream.IntStream.range(startInclusive, endExclusive)
                .mapToObj(index -> (Object) Map.of(
                        "userId", index % 3 == 0 ? "1" : "2",
                        "managementId", "management-" + index,
                        "username", "user-" + index,
                        "tenantId", "tenant-a",
                        "activeTenantId", "tenant-a"
                ))
                .toList();
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

    private SecurityProperties localSecurityProperties() {
        return new SecurityProperties(
                Duration.ofDays(7),
                Duration.ofMinutes(1),
                false,
                "Lax",
                new SecurityProperties.Redis(false, false, false, "eap:test:", "v1", Duration.ofSeconds(30))
        );
    }
}
