package com.enterprise.auth.platform.common.convention.result;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "统一接口响应")
public record ApiResponse<T>(
        @Schema(description = "业务状态码") String code,
        @Schema(description = "是否成功") boolean success,
        @Schema(description = "响应数据") T data,
        @Schema(description = "响应消息") String message
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", true, data, "成功");
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>("OK", true, null, "成功");
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(code, false, null, message);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return fail("BUSINESS_ERROR", message);
    }
}
