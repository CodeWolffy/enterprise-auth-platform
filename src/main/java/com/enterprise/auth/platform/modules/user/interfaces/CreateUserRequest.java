package com.enterprise.auth.platform.modules.user.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(description = "新增用户请求")
public record CreateUserRequest(
        @Schema(description = "用户名，5-16 位", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 5, max = 16) String username,
        @Schema(description = "显示名称") String displayName,
        @Schema(description = "手机号") String mobile,
        @Schema(description = "邮箱") String email,
        @Schema(description = "登录密码，创建时必填，修改时留空表示不变") String password,
        @Schema(description = "所属部门ID") Long deptId,
        @Schema(description = "是否启用，默认 true") Boolean enabled,
        @Schema(description = "初始角色编码集合") Set<@NotBlank String> roleCodes
) {
}
