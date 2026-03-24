package com.enterprise.auth.platform.common.exception;

import com.enterprise.auth.platform.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusiness(BusinessException exception) {
        return ApiResponse.fail(exception.code(), exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(Exception exception) {
        return ApiResponse.fail("VALIDATION_ERROR", validationMessage(exception));
    }
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleDenied(AccessDeniedException exception, HttpServletRequest request) {
        if (exception instanceof CsrfException) {
            log.warn("CSRF validation failed for {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
            // 返回不带 code 的响应，触发前端 http.ts 拦截器中的 CSRF 重试逻辑
            return ApiResponse.fail(null, "CSRF 令牌不匹配或缺失");
        }
        log.warn("Access denied for {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
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
        log.error("Unhandled server exception", exception);
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
}