package com.enterprise.auth.platform.modules.dept.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "部门新增或修改请求")
public record DeptCrudRequest(
        @Schema(description = "父部门ID") Long parentId,
        @Schema(description = "部门编码") String deptCode,
        @Schema(description = "部门名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String deptName,
        @Schema(description = "负责人用户ID") Long leaderUserId,
        @Schema(description = "负责人姓名") String leaderName,
        @Schema(description = "负责人电话") @Pattern(regexp = "^[0-9+\\-\\s()]{0,32}$", message = "负责人电话格式不正确") String leaderPhone,
        @Schema(description = "排序序号") @Min(value = 0, message = "排序不能小于 0") Integer orderNo,
        @Schema(description = "启用状态：0 停用，1 启用") @Min(0) @Max(1) Integer enabled,
        @Schema(description = "目标租户编码，仅平台管理员新增时生效") String tenantId
) {
    public DeptCrudRequest(
            Long parentId,
            String deptCode,
            String deptName,
            Long leaderUserId,
            String leaderName,
            String leaderPhone,
            Integer orderNo,
            Integer enabled
    ) {
        this(parentId, deptCode, deptName, leaderUserId, leaderName, leaderPhone, orderNo, enabled, null);
    }

    public DeptCrudRequest(Long parentId, String deptCode, String deptName, Long leaderUserId) {
        this(parentId, deptCode, deptName, leaderUserId, null, null, null, null, null);
    }
}
