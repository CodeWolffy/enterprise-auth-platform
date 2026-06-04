package com.enterprise.auth.platform.modules.user.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "当前账号资料更新请求")
public record AccountProfileUpdateRequest(
        @Schema(description = "显示名称")
        @Size(max = 32, message = "显示名称不能超过32个字符")
        String displayName,
        @Schema(description = "手机号")
        @Pattern(regexp = "^$|^1\\d{10}$", message = "请输入有效的 11 位手机号")
        String mobile,
        @Schema(description = "邮箱")
        @Email(message = "请输入有效的邮箱地址")
        @Size(max = 128, message = "邮箱不能超过128个字符")
        String email
) {
}