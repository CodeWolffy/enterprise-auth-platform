package com.enterprise.auth.platform.audit.controller;

import com.enterprise.auth.platform.audit.model.AuditPage;
import com.enterprise.auth.platform.audit.model.AuditQuery;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "安全审计")
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @Operation(summary = "分页查询审计事件")
    @GetMapping("/events")
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<AuditPage> events(
            @Parameter(description = "租户编码") @RequestParam(required = false) String tenantId,
            @Parameter(description = "事件类型") @RequestParam(required = false) String eventType,
            @Parameter(description = "操作人") @RequestParam(required = false) String operator,
            @Parameter(description = "请求ID") @RequestParam(required = false) String requestId,
            @Parameter(description = "发生开始时间，ISO-8601 格式") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @Parameter(description = "发生结束时间，ISO-8601 格式") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(auditService.query(new AuditQuery(
                tenantId,
                eventType,
                operator,
                requestId,
                occurredFrom,
                occurredTo,
                page,
                size
        )));
    }
}
