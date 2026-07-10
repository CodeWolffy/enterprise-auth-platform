package com.enterprise.auth.platform.modules.dept.application;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "部门目录项")
public record DepartmentView(
        @Schema(description = "部门 ID") Long id,
        @Schema(description = "租户编码") String tenantId,
        @Schema(description = "部门编码") String code,
        @Schema(description = "部门名称") String name,
        @Schema(description = "父部门 ID") Long parentId,
        @Schema(description = "负责人用户 ID") Long leaderUserId,
        @Schema(description = "负责人姓名") String leaderName,
        @Schema(description = "负责人电话") String leaderPhone,
        @Schema(description = "排序序号") Integer orderNo,
        @Schema(description = "启用状态：0 停用，1 启用") Integer enabled
) {
}
