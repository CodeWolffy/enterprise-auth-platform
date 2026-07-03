package com.enterprise.auth.platform.modules.user.interfaces;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;

@Schema(description = "用户列表项")
public record UserSummary(
        @Schema(description = "用户ID") Long id,
        @Schema(description = "租户编码") String tenantId,
        @Schema(description = "用户名") String username,
        @Schema(description = "显示名称") String displayName,
        @Schema(description = "手机号") String mobile,
        @Schema(description = "邮箱") String email,
        @Schema(description = "部门ID") Long deptId,
        @Schema(description = "是否启用") boolean enabled,
        @Schema(description = "角色编码集合") Set<String> roles,
        @Schema(description = "权限编码集合") Set<String> permissions,
        @Schema(description = "数据权限范围") DataScopeType dataScopeType,
        @Schema(description = "创建时间，ISO-8601 UTC") Instant createdAt,
        @Schema(description = "最后登录时间，ISO-8601 UTC") Instant lastLoginAt,
        @Schema(description = "最后登录 IP") String lastLoginIp
) {
}
