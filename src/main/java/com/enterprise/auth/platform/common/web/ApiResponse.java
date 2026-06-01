package com.enterprise.auth.platform.common.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "统一接口响应")
public record ApiResponse<T>(
        @Schema(description = "业务状态码") String code,
        @Schema(description = "是否成功") boolean success,
        @Schema(description = "响应数据") T data,
        @Schema(description = "响应消息") String message,
        @Schema(description = "错误详情") List<ErrorDetail> details,
        @Schema(description = "请求追踪 ID") String requestId,
        @Schema(description = "请求路径") String path,
        @Schema(description = "响应时间") Instant timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", true, data, "成功", List.of(), null, null, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>("OK", true, null, "成功", List.of(), null, null, null);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return fail(code, message, List.of(), null, null);
    }

    public static <T> ApiResponse<T> fail(String code, String message, List<ErrorDetail> details, String requestId, String path) {
        return new ApiResponse<>(
                code,
                false,
                null,
                message,
                details == null ? List.of() : List.copyOf(details),
                requestId,
                path,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> fail(String message) {
        return fail("BUSINESS_ERROR", message);
    }

    @Schema(description = "错误明细")
    public record ErrorDetail(
            @Schema(description = "错误字段或对象") String target,
            @Schema(description = "错误信息") String message,
            @Schema(description = "错误类型") String type
    ) {
        public static ErrorDetail of(String target, String message, String type) {
            return new ErrorDetail(target, message, type);
        }
    }
}