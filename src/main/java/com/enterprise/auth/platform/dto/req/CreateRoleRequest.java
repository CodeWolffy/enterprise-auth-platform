package com.enterprise.auth.platform.dto.req;

import com.enterprise.auth.platform.dto.model.DataScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "新增角色请求")
public record CreateRoleRequest(
        @Schema(description = "角色编码", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String roleCode,
        @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String roleName,
        @Schema(description = "角色描述") String roleDesc,
        @Schema(description = "数据权限范围", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull DataScopeType dataScopeType,
        @Schema(description = "自定义部门 ID 集合，仅 CUSTOM 范围时生效") List<Long> customDeptIds
) {
}
