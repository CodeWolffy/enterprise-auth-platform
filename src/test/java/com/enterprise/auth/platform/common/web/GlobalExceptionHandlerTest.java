package com.enterprise.auth.platform.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.RateLimitInterceptor.RateLimitExceededException;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    @Test
    void shouldUseExceptionRetryAfterSecondsForRateLimitResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(AuditService.class));
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
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(AuditService.class));

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
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(AuditService.class));
        List<ApiResponse.ErrorDetail> details = List.of(ApiResponse.ErrorDetail.of("username", "不能为空", "field"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(
                new BusinessException("VALIDATION_ERROR", "请求参数校验失败", HttpStatus.BAD_REQUEST, details),
                request()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().details()).containsExactlyElementsOf(details);
    }

    @Test
    void shouldReturnStructuredUnexpectedError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(AuditService.class));

        ResponseEntity<ApiResponse<Void>> response = handler.handleUnexpected(new RuntimeException("boom"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("服务器内部错误");
        assertThat(response.getBody().message()).doesNotContain("boom");
        assertThat(response.getBody().details()).isEmpty();
        assertThat(response.getBody().requestId()).isEqualTo("test-request-id");
        assertThat(response.getBody().path()).isEqualTo("/api/test");
    }

    @Test
    void shouldNotReturnNullForClientAbort() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(AuditService.class));

        ResponseEntity<ApiResponse<Void>> response = handler.handleUnexpected(new RuntimeException("Broken pipe"), request());

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("X-Request-Id", "test-request-id");
        return request;
    }
}