package com.enterprise.auth.platform.common.exception;

import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.common.web.RateLimitInterceptor.RateLimitExceededException;
import com.enterprise.auth.platform.tenant.TenantContext;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.springframework.util.StringUtils;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String RATE_LIMITED_CODE = "RATE_LIMITED";
    private final AuditService auditService;

    public GlobalExceptionHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusiness(BusinessException exception) {
        return ApiResponse.fail(exception.code(), exception.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(ApiResponse.fail(RATE_LIMITED_CODE, exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(Exception exception) {
        return ApiResponse.fail("VALIDATION_ERROR", validationMessage(exception));
    }
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleDenied(AccessDeniedException exception, HttpServletRequest request) {
        recordSecurityDenyEvent(exception, request);
        if (exception instanceof CsrfException) {
            log.warn("CSRF 校验失败 {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
            // 返回不带 code 的响应，触发前端 http.ts 拦截器中的 CSRF 重试逻辑
            return ApiResponse.fail(null, "CSRF 令牌不匹配或缺失");
        }
        log.warn("访问被拒绝 {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        return ApiResponse.fail("ACCESS_DENIED", "无权访问此资源");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(NoResourceFoundException exception) {
        return ApiResponse.fail("RESOURCE_NOT_FOUND", "资源不存在");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnexpected(Exception exception) {
        log.error("未处理的服务器异常", exception);
        return ApiResponse.fail("INTERNAL_ERROR", "服务器内部错误");
    }

    private String validationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            return fieldErrors(methodArgumentNotValidException.getBindingResult().getFieldErrors());
        }
        if (exception instanceof BindException bindException) {
            return fieldErrors(bindException.getBindingResult().getFieldErrors());
        }
        if (exception instanceof ConstraintViolationException constraintViolationException) {
            return constraintViolationException.getConstraintViolations().stream()
                    .map(violation -> violation.getMessage())
                    .findFirst()
                    .orElse("请求参数校验失败");
        }
        if (exception instanceof MethodArgumentTypeMismatchException mismatchException) {
            String parameter = mismatchException.getName();
            return parameter + ":参数格式不正确";
        }
        return "请求参数校验失败";
    }

    private String fieldErrors(List<FieldError> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return "请求参数校验失败";
        }
        return fieldErrors.stream()
                .map(error -> error.getField() + ":" + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
    }

    private void recordSecurityDenyEvent(AccessDeniedException exception, HttpServletRequest request) {
        try {
            Principal principal = request.getUserPrincipal();
            String operator = principal == null ? "anonymous" : principal.getName();
            String tenantId = StringUtils.hasText(TenantContext.getTenantId()) ? TenantContext.getTenantId() : "platform";
            Map<String, Object> payload = Map.of(
                    "method", request.getMethod(),
                    "path", request.getRequestURI(),
                    "reason", exception instanceof CsrfException ? "csrf_failed" : "access_denied",
                    "origin", String.valueOf(request.getHeader("Origin")),
                    "referer", String.valueOf(request.getHeader("Referer")),
                    "userAgent", String.valueOf(request.getHeader("User-Agent"))
            );
            auditService.record("SECURITY_ACCESS_DENIED", operator, tenantId, payload);
        } catch (Exception ignored) {
            // Keep exception handling resilient even when audit storage is unavailable.
        }
    }
}
