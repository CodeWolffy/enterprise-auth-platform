package com.enterprise.auth.platform.common.exception;

import com.enterprise.auth.platform.common.web.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final List<ApiResponse.ErrorDetail> details;

    public BusinessException(String message) {
        this("BUSINESS_ERROR", message);
    }

    public BusinessException(String code, String message) {
        this(code, message, resolveStatus(code), List.of());
    }

    public BusinessException(String code, String message, HttpStatus status) {
        this(code, message, status, List.of());
    }

    public BusinessException(String code, String message, HttpStatus status, List<ApiResponse.ErrorDetail> details) {
        super(message);
        this.code = code;
        this.status = status == null ? resolveStatus(code) : status;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public List<ApiResponse.ErrorDetail> details() {
        return details;
    }

    private static HttpStatus resolveStatus(String code) {
        if (code == null) {
            return HttpStatus.BAD_REQUEST;
        }
        return switch (code) {
            case "ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
            case "UNAUTHORIZED", "AUTH_ERROR", "INVALID_TOKEN", "SESSION_EXPIRED", "SESSION_OFFLINE" -> HttpStatus.UNAUTHORIZED;
            case "NOT_FOUND", "RESOURCE_NOT_FOUND", "TENANT_NOT_FOUND", "SESSION_NOT_FOUND", "USER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CONFLICT", "DUPLICATE_RESOURCE", "DUPLICATE_USERNAME", "TENANT_DISABLED" -> HttpStatus.CONFLICT;
            case "RATE_LIMITED", "REGISTER_RATE_LIMITED", "ACCOUNT_LOCKED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "VALIDATION_ERROR" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}