package com.enterprise.auth.platform.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "审计导出保留策略请求")
public record AuditExportPolicyRequest(
        @Schema(description = "导出结果保留天数", example = "7")
        @Min(value = 1, message = "保留天数不能小于 1")
        @Max(value = 365, message = "保留天数不能超过 365")
        Integer retentionDays,
        @Schema(description = "单租户最多保留任务数", example = "100")
        @Min(value = 1, message = "最多保留任务数不能小于 1")
        @Max(value = 5000, message = "最多保留任务数不能超过 5000")
        Integer maxTasks
) {
}
