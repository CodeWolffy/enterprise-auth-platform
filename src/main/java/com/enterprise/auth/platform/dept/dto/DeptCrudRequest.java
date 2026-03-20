package com.enterprise.auth.platform.dept.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "部门新增或修改请求")
public record DeptCrudRequest(
        @Schema(description = "父部门ID") Long parentId,
        @Schema(description = "部门编码") String deptCode,
        @Schema(description = "部门名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String deptName,
        @Schema(description = "负责人用户ID") Long leaderUserId
) {
}
