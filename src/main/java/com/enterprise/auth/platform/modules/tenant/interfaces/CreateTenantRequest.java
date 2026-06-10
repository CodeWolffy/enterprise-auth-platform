package com.enterprise.auth.platform.modules.tenant.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Schema(description = "新增租户请求")
public record CreateTenantRequest(
        @Schema(description = "租户编码", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String tenantId,
        @Schema(description = "租户名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String tenantName,
        @Schema(description = "是否平台级租户") boolean platformLevel,
        @Schema(description = "租户状态，1 启用，0 禁用") Integer tenantStatus,
        @Schema(description = "授权开始时间") Long authBeginAt,
        @Schema(description = "授权结束时间") Long expireAt,
        @Schema(description = "套餐编码") String packageCode,
        @Schema(description = "套餐名称") String packageName,
        @Schema(description = "用户配额") Integer userQuota,
        @Schema(description = "存储配额，单位 GB") Integer storageQuotaGb,
        @Schema(description = "启用的能力编码集合") List<String> capabilityCodes,
        @Schema(description = "Logo 地址") String logoUrl,
        @Schema(description = "联系人姓名") String contactName,
        @Schema(description = "联系人电话") String contactPhone,
        @Schema(description = "联系人邮箱") String contactEmail,
        @Schema(description = "官网地址") String website,
        @Schema(description = "联系地址") String address,
        @Schema(description = "运营备注") String lifecycleNote
) {
}
