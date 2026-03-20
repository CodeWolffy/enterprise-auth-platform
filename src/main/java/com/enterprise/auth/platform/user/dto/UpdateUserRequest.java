package com.enterprise.auth.platform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(description = "修改用户请求")
public record UpdateUserRequest(
        @Schema(description = "显示名称") String displayName,
        @Schema(description = "手机号") String mobile,
        @Schema(description = "邮箱") String email,
        @Schema(description = "所属部门ID") Long deptId,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "新密码，长度 8-64 位") @Size(min = 8, max = 64) String password,
        @Schema(description = "角色编码集合，为空则不修改") Set<String> roleCodes
) {
}
