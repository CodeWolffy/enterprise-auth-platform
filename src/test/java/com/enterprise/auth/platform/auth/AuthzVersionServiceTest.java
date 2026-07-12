package com.enterprise.auth.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.modules.auth.application.AuthzVersionService;
import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuthzVersionServiceTest {

    private static final String GLOBAL_KEY = "eap:test:v1:authz:version:global";
    private static final String TENANT_KEY = "eap:test:v1:authz:version:tenant:tenant-a";

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @SuppressWarnings("unchecked")
    void currentVersionsShouldUseOneMultiGetAndReuseWithinRequest() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of(GLOBAL_KEY, TENANT_KEY))).thenReturn(List.of("7", "11"));
        AuthzVersionService service = new AuthzVersionService(redisTemplate, securityProperties());
        bindRequest();

        AuthzVersionService.Versions first = service.currentVersions("tenant-a");
        AuthzVersionService.Versions second = service.currentVersions("tenant-a");

        assertThat(first).isEqualTo(new AuthzVersionService.Versions(7L, 11L));
        assertThat(second).isEqualTo(first);
        assertThat(service.currentGlobalVersion()).isEqualTo(7L);
        assertThat(service.currentTenantVersion("tenant-a")).isEqualTo(11L);
        verify(valueOperations, times(1)).multiGet(List.of(GLOBAL_KEY, TENANT_KEY));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aNewRequestShouldReadLatestVersions() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of(GLOBAL_KEY, TENANT_KEY)))
                .thenReturn(List.of("1", "2"), List.of("3", "4"));
        AuthzVersionService service = new AuthzVersionService(redisTemplate, securityProperties());

        bindRequest();
        assertThat(service.currentVersions("tenant-a"))
                .isEqualTo(new AuthzVersionService.Versions(1L, 2L));
        bindRequest();
        assertThat(service.currentVersions("tenant-a"))
                .isEqualTo(new AuthzVersionService.Versions(3L, 4L));

        verify(valueOperations, times(2)).multiGet(List.of(GLOBAL_KEY, TENANT_KEY));
    }

    @Test
    @SuppressWarnings("unchecked")
    void currentVersionsFreshShouldBypassRequestCache() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of(GLOBAL_KEY, TENANT_KEY)))
                .thenReturn(List.of("1", "2"), List.of("3", "4"));
        AuthzVersionService service = new AuthzVersionService(redisTemplate, securityProperties());
        bindRequest();

        assertThat(service.currentVersions("tenant-a"))
                .isEqualTo(new AuthzVersionService.Versions(1L, 2L));
        assertThat(service.currentVersionsFresh("tenant-a"))
                .isEqualTo(new AuthzVersionService.Versions(3L, 4L));

        verify(valueOperations, times(2)).multiGet(List.of(GLOBAL_KEY, TENANT_KEY));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bumpShouldInvalidateCurrentRequestCache() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of(GLOBAL_KEY, TENANT_KEY)))
                .thenReturn(List.of("5", "8"), List.of("5", "9"));
        when(valueOperations.increment(TENANT_KEY)).thenReturn(9L);
        AuthzVersionService service = new AuthzVersionService(redisTemplate, securityProperties());
        bindRequest();

        assertThat(service.currentVersions("tenant-a").tenant()).isEqualTo(8L);
        assertThat(service.bumpTenant("tenant-a")).isEqualTo(9L);
        assertThat(service.currentVersions("tenant-a").tenant()).isEqualTo(9L);

        verify(redisTemplate).expire(TENANT_KEY, 365, TimeUnit.DAYS);
        verify(valueOperations, times(2)).multiGet(List.of(GLOBAL_KEY, TENANT_KEY));
    }

    private void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
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
