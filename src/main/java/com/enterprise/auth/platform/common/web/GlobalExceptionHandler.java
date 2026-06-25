package com.enterprise.auth.platform.common.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.SaTokenException;
import com.enterprise.auth.platform.common.context.RequestContext;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.ApiResponse.ErrorDetail;
import com.enterprise.auth.platform.common.web.RateLimitInterceptor.RateLimitExceededException;
import com.enterprise.auth.platform.modules.log.application.LogPublisher;
import com.enterprise.auth.platform.modules.log.domain.event.LogEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String RATE_LIMITED_CODE = "RATE_LIMITED";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private final LogPublisher logPublisher;
    private final IpLocationResolver ipLocationResolver;

    public GlobalExceptionHandler(LogPublisher logPublisher, IpLocationResolver ipLocationResolver) {
        this.logPublisher = logPublisher;
        this.ipLocationResolver = ipLocationResolver;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception, HttpServletRequest request) {
        return fail(exception.status(), exception.code(), exception.getMessage(), exception.details(), request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(exception.retryAfterSeconds()))
                .body(errorBody(RATE_LIMITED_CODE, exception.getMessage(), List.of(), request));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLogin(NotLoginException exception, HttpServletRequest request) {
        return fail(HttpStatus.UNAUTHORIZED, notLoginCode(exception), exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<ApiResponse<Void>> handleDenied(NotPermissionException exception, HttpServletRequest request) {
        recordSecurityDenyEvent(exception, request);
        log.warn("Access denied {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        return fail(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权限访问", List.of(), request);
    }

    @ExceptionHandler(SaTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleSaToken(SaTokenException exception, HttpServletRequest request) {
        log.warn("Sa-Token authentication error: {}", exception.getMessage());
        return fail(HttpStatus.UNAUTHORIZED, "AUTH_ERROR", "Authentication failed", List.of(), request);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception exception, HttpServletRequest request) {
        return fail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", validationMessage(exception), validationDetails(exception), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        return fail(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资源未找到", List.of(), request);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleClientAbort(AsyncRequestNotUsableException exception) {
        log.debug("Client disconnected before the response could be written: {}", exception.getMessage());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        if (isClientAbort(exception)) {
            log.debug("Client disconnected before the response could be written: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        String requestId = requestId(request);
        log.error("Unhandled server exception, requestId={}", requestId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("INTERNAL_ERROR", "服务器内部错误", List.of(), requestId, requestPath(request)));
    }

    private ResponseEntity<ApiResponse<Void>> fail(
            HttpStatus status,
            String code,
            String message,
            List<ErrorDetail> details,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(errorBody(code, message, details, request));
    }

    private ApiResponse<Void> errorBody(String code, String message, List<ErrorDetail> details, HttpServletRequest request) {
        return ApiResponse.fail(code, message, details, requestId(request), requestPath(request));
    }

    private String validationMessage(Exception exception) {
        List<ErrorDetail> details = validationDetails(exception);
        if (!details.isEmpty()) {
            return details.stream()
                    .map(detail -> detail.target() + ":" + detail.message())
                    .collect(Collectors.joining("; "));
        }
        return "请求参数校验失败";
    }

    private List<ErrorDetail> validationDetails(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            return fieldErrors(methodArgumentNotValidException.getBindingResult().getFieldErrors());
        }
        if (exception instanceof BindException bindException) {
            return fieldErrors(bindException.getBindingResult().getFieldErrors());
        }
        if (exception instanceof ConstraintViolationException constraintViolationException) {
            return constraintViolationException.getConstraintViolations().stream()
                    .map(violation -> ErrorDetail.of(violation.getPropertyPath().toString(), violation.getMessage(), "constraint"))
                    .toList();
        }
        if (exception instanceof MethodArgumentTypeMismatchException mismatchException) {
            return List.of(ErrorDetail.of(mismatchException.getName(), "参数格式无效", "type_mismatch"));
        }
        if (exception instanceof IllegalArgumentException illegalArgumentException) {
            return List.of(ErrorDetail.of("request", illegalArgumentException.getMessage(), "invalid_argument"));
        }
        return List.of();
    }

    private List<ErrorDetail> fieldErrors(List<FieldError> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return List.of();
        }
        return fieldErrors.stream()
                .map(error -> ErrorDetail.of(error.getField(), error.getDefaultMessage(), "field"))
                .toList();
    }

    private String notLoginCode(NotLoginException exception) {
        return switch (exception.getType()) {
            case NotLoginException.TOKEN_TIMEOUT -> "SESSION_EXPIRED";
            case NotLoginException.KICK_OUT, NotLoginException.BE_REPLACED -> "SESSION_OFFLINE";
            case NotLoginException.INVALID_TOKEN, NotLoginException.NO_PREFIX -> "INVALID_TOKEN";
            default -> "UNAUTHORIZED";
        };
    }

    private String requestId(HttpServletRequest request) {
        if (request == null) {
            return UUID.randomUUID().toString();
        }
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(requestId)) {
            return requestId;
        }
        Object attribute = request.getAttribute(REQUEST_ID_HEADER);
        if (attribute != null && StringUtils.hasText(String.valueOf(attribute))) {
            return String.valueOf(attribute);
        }
        return UUID.randomUUID().toString();
    }

    private String requestPath(HttpServletRequest request) {
        return request == null ? "" : request.getRequestURI();
    }

    private boolean isClientAbort(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String className = current.getClass().getName();
            if (className.equals("org.apache.catalina.connector.ClientAbortException")
                    || className.equals("org.eclipse.jetty.io.EofException")
                    || className.equals("java.io.EOFException")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (
                    message.contains("Broken pipe")
                            || message.contains("Connection reset")
                            || message.contains("你的主机中的软件中止了一个已建立的连接"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void recordSecurityDenyEvent(Exception exception, HttpServletRequest request) {
        try {
            Principal principal = request.getUserPrincipal();
            String operator;
            if (principal != null && StringUtils.hasText(principal.getName())) {
                operator = principal.getName();
            } else {
                operator = "anonymous";
            }
            String tenantId = StringUtils.hasText(TenantContext.getTenantId()) ? TenantContext.getTenantId() : "platform";
            Map<String, Object> payload = Map.of(
                    "method", request.getMethod(),
                    "path", request.getRequestURI(),
                    "reason", "access_denied",
                    "origin", String.valueOf(request.getHeader("Origin")),
                    "referer", String.valueOf(request.getHeader("Referer")),
                    "userAgent", String.valueOf(request.getHeader("User-Agent"))
            );
            String clientIp = RequestContext.getClientIp();
            String location = ipLocationResolver.resolve(clientIp);
            String method = request.getMethod();
            String requestUri = request.getRequestURI();
            logPublisher.publish(new LogEvent("拒绝访问", operator, tenantId, payload, null, clientIp, location, method, requestUri, null, null, null, null));
        } catch (Exception ex) {
            log.debug("Failed to record security deny log event. method={}, path={}, error={}",
                    request.getMethod(), request.getRequestURI(), ex.getMessage());
        }
    }
}
