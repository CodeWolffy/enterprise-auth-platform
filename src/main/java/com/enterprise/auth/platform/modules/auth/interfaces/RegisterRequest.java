package com.enterprise.auth.platform.modules.auth.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "用户注册请求")
public record RegisterRequest(
        @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 3, max = 50) String username,

        @Schema(description = "显示名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 2, max = 100) String displayName,

        @Schema(description = "登录密码，至少8位，包含字母和数字", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String password,

        @Schema(description = "手机号") String mobile,

        @Schema(description = "邮箱") String email
) {
}
