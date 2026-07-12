package com.enterprise.auth.platform.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.RateLimitInterceptor.RateLimitExceededException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final IpLocationResolver ipLocationResolver = mock(IpLocationResolver.class);

    @Test
    void shouldUseExceptionRetryAfterSecondsForRateLimitResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(eventPublisher, ipLocationResolver);
        MockHttpServletRequest request = request();

        ResponseEntity<ApiResponse<Void>> response = handler.handleRateLimit(
                new RateLimitExceededException("too many requests", 300),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("300");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RATE_LIMITED");
        assertThat(response.getBody().message()).isEqualTo("too many requests");
        assertThat(response.getBody().requestId()).isEqualTo("test-request-id");
        assertThat(response.getBody().path()).isEqualTo("/api/test");
    }

    @Test
    void shouldMapBusinessExceptionStatusByCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(eventPublisher, ipLocationResolver);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(
                new BusinessException("ACCESS_DENIED", "无权访问"),
                request()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ACCESS_DENIED");
        assertThat(response.getBody().message()).isEqualTo("无权访问");
    }

    @Test
    void shouldKeepBusinessExceptionDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(eventPublisher, ipLocationResolver);
        List<ApiResponse.ErrorDetail> details = List.of(ApiResponse.ErrorDetail.of("username", "不能为空", "field"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(
                new BusinessException("VALIDATION_ERROR", "请求参数校验失败", HttpStatus.BAD_REQUEST, details),
                request()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().details()).containsExactlyElementsOf(details);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        request.addHeader("X-Request-Id", "test-request-id");
        return request;
    }
}