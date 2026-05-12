package com.enterprise.auth.platform.common.convention.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.enterprise.auth.platform.service.AuditService;
import com.enterprise.auth.platform.common.convention.result.ApiResponse;
import com.enterprise.auth.platform.common.convention.web.RateLimitInterceptor.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    @Test
    void shouldUseExceptionRetryAfterSecondsForRateLimitResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(AuditService.class));

        ResponseEntity<ApiResponse<Void>> response = handler.handleRateLimit(
                new RateLimitExceededException("too many requests", 300)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("300");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RATE_LIMITED");
        assertThat(response.getBody().message()).isEqualTo("too many requests");
    }
}
