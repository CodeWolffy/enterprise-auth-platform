package com.enterprise.auth.platform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(description = "新增用户请求")
public record CreateUserRequest(
        @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String username,
        @Schema(description = "显示名称") String displayName,
        @Schema(description = "手机号") String mobile,
        @Schema(description = "邮箱") String email,
        @Schema(description = "登录密码，长度 8-64 位", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 8, max = 64) String password,
        @Schema(description = "所属部门ID") Long deptId,
        @Schema(description = "是否启用，默认 true") Boolean enabled,
        @Schema(description = "初始角色编码集合") Set<@NotBlank String> roleCodes
) {
}
