package com.enterprise.auth.platform.security;

import com.enterprise.auth.platform.infrastructure.security.SaTokenUserContextInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.web.ClientIpResolver;
import com.enterprise.auth.platform.infrastructure.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SaTokenUserContextInterceptorTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void afterCompletionShouldClearTenantContext() {
        SaTokenUserContextInterceptor interceptor = new SaTokenUserContextInterceptor(
                null,
                null,
                new ClientIpResolver(properties())
        );
        TenantContext.setTenantId("tenant-a");

        interceptor.afterCompletion(
                mock(HttpServletRequest.class),
                mock(HttpServletResponse.class),
                new Object(),
                null
        );

        assertThat(TenantContext.getTenantId()).isNull();
    }

    private RateLimitProperties properties() {
        return new RateLimitProperties(
                true,
                20,
                20,
                Duration.ofMinutes(1),
                RateLimitProperties.FailureMode.OPEN,
                List.of(),
                null
        );
    }
}