package com.enterprise.auth.platform.audit.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "审计分页结果")
public record AuditPage(
        @Schema(description = "总记录数") long total,
        @Schema(description = "当前页码") int page,
        @Schema(description = "每页数量") int size,
        @Schema(description = "当前页记录") List<AuditEvent> records
) {
}
