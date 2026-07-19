package com.enterprise.auth.platform.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.common.context.RequestContext;
import com.enterprise.auth.platform.infrastructure.config.RateLimitProperties;
import com.enterprise.auth.platform.modules.auth.domain.AuthContextHolder;
import com.enterprise.auth.platform.modules.auth.domain.SessionPrincipal;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantFilterTest {

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void shouldClearAuthenticationContextWhenDownstreamFails() {
        RateLimitProperties properties = new RateLimitProperties(
                true,
                20,
                20,
                Duration.ofMinutes(1),
                RateLimitProperties.FailureMode.OPEN,
                List.of(),
                null
        );
        TenantFilter filter = new TenantFilter(new ClientIpResolver(properties), AuthContextHolder::clear);
        UserAccount user = new UserAccount(
                1L, "tenant-a", "alice", "hash", true,
                Set.of(), Set.of(), Set.of(), DataScopeType.ALL, 1
        );
        AuthContextHolder.set(user, new SessionPrincipal("session-a", "tenant-a", "tenant-a", false));

        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) -> {
                    throw new IllegalStateException("boom");
                }
        )).isInstanceOf(IllegalStateException.class);

        assertThat(AuthContextHolder.currentUser()).isEmpty();
        assertThat(AuthContextHolder.currentSession()).isEmpty();
    }

    @Test
    void shouldReplaceInvalidRequestIdAndReuseItEverywhere() throws Exception {
        RateLimitProperties properties = new RateLimitProperties(
                true, 20, 20, Duration.ofMinutes(1),
                RateLimitProperties.FailureMode.OPEN, List.of(), null
        );
        TenantFilter filter = new TenantFilter(new ClientIpResolver(properties), AuthContextHolder::clear);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdSupport.HEADER, "invalid request id with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] observed = new String[2];

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            observed[0] = RequestContext.getRequestId();
            observed[1] = String.valueOf(servletRequest.getAttribute(RequestIdSupport.HEADER));
        });

        assertThat(observed[0]).matches("[A-Za-z0-9._-]{1,64}");
        assertThat(observed[1]).isEqualTo(observed[0]);
        assertThat(response.getHeader(RequestIdSupport.HEADER)).isEqualTo(observed[0]);
    }
}
