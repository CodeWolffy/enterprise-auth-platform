package com.enterprise.auth.platform.modules.tenant.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "租户套餐维护请求")
public record TenantPackageCrudRequest(
        @Schema(description = "套餐编码") @NotBlank String packageCode,
        @Schema(description = "套餐名称") @NotBlank String packageName,
        @Schema(description = "运营副标题") String subtitle,
        @Schema(description = "销售价") BigDecimal salesPrice,
        @Schema(description = "原价") BigDecimal originalPrice,
        @Schema(description = "富文本或 Markdown 描述") String descriptionMd,
        @Schema(description = "应用标识") String appKey,
        @Schema(description = "展示排序") Integer orderNo,
        @Schema(description = "用户配额") Integer userQuota,
        @Schema(description = "存储配额 GB") Integer storageQuotaGb,
        @Schema(description = "套餐说明") String packageDesc,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "套餐包含的能力编码集合") List<String> capabilityCodes
) {
}
