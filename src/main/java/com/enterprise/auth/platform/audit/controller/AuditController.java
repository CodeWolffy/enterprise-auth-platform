package com.enterprise.auth.platform.audit.controller;

import com.enterprise.auth.platform.audit.model.AuditEvent;
import com.enterprise.auth.platform.audit.model.AuditPage;
import com.enterprise.auth.platform.audit.model.AuditQuery;
import com.enterprise.auth.platform.audit.service.AuditExportTaskService;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.common.model.PageResult;
import com.enterprise.auth.platform.security.SecuritySupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "安全审计")
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;
    private final AuditExportTaskService auditExportTaskService;

    public AuditController(AuditService auditService, AuditExportTaskService auditExportTaskService) {
        this.auditService = auditService;
        this.auditExportTaskService = auditExportTaskService;
    }

    @Operation(summary = "分页查询审计事件")
    @GetMapping("/events")
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<AuditPage> events(
            @Parameter(description = "租户编码") @RequestParam(required = false) String tenantId,
            @Parameter(description = "事件类型") @RequestParam(required = false) String eventType,
            @Parameter(description = "操作人") @RequestParam(required = false) String operator,
            @Parameter(description = "请求 ID") @RequestParam(required = false) String requestId,
            @Parameter(description = "客户端 IP") @RequestParam(required = false) String clientIp,
            @Parameter(description = "发生开始时间，ISO-8601 格式")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant occurredFrom,
            @Parameter(description = "发生结束时间，ISO-8601 格式")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant occurredTo,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(auditService.query(buildQuery(tenantId, eventType, operator, requestId, clientIp, occurredFrom, occurredTo, page, size)));
    }

    @Operation(summary = "导出审计事件")
    @GetMapping("/events/export")
    @PreAuthorize("hasAuthority('audit:read')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo
    ) {
        AuditQuery query = buildQuery(tenantId, eventType, operator, requestId, clientIp, occurredFrom, occurredTo, 1, 2000);
        List<AuditEvent> records = auditService.export(query);
        auditService.record(
                "AUDIT_EXPORTED",
                SecuritySupport.currentOperator(),
                StringUtils.hasText(tenantId) ? tenantId : "platform",
                java.util.Map.of(
                        "eventType", eventType == null ? "" : eventType,
                        "operator", operator == null ? "" : operator,
                        "clientIp", clientIp == null ? "" : clientIp,
                        "requestId", requestId == null ? "" : requestId,
                        "occurredFrom", String.valueOf(occurredFrom),
                        "occurredTo", String.valueOf(occurredTo),
                        "recordCount", records.size()
                )
        );
        String csv = buildCsv(records);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-events.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "创建异步审计导出任务")
    @PostMapping("/exports")
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<AuditExportTaskService.ExportTaskView> createExportTask(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo
    ) {
        return ApiResponse.ok(auditExportTaskService.create(buildQuery(tenantId, eventType, operator, requestId, clientIp, occurredFrom, occurredTo, 1, 2000)));
    }

    @Operation(summary = "分页查询审计导出任务")
    @GetMapping("/exports")
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<PageResult<AuditExportTaskService.ExportTaskView>> exportTasks(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(auditExportTaskService.page(tenantId, status, page, size));
    }

    @Operation(summary = "下载异步审计导出文件")
    @GetMapping("/exports/{taskId}/download")
    @PreAuthorize("hasAuthority('audit:read')")
    public ResponseEntity<byte[]> downloadExportTask(@PathVariable Long taskId) {
        AuditExportTaskService.DownloadFile file = auditExportTaskService.download(taskId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.fileName())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(file.content());
    }

    private AuditQuery buildQuery(
            String tenantId,
            String eventType,
            String operator,
            String requestId,
            String clientIp,
            Instant occurredFrom,
            Instant occurredTo,
            int page,
            int size
    ) {
        return new AuditQuery(tenantId, eventType, operator, requestId, clientIp, occurredFrom, occurredTo, page, size);
    }

    private String buildCsv(List<AuditEvent> records) {
        StringBuilder builder = new StringBuilder();
        builder.append("type,operator,tenantId,requestId,clientIp,occurredAt,details\n");
        for (AuditEvent record : records) {
            appendCell(builder, record.type());
            appendCell(builder, record.operator());
            appendCell(builder, record.tenantId());
            appendCell(builder, record.requestId());
            appendCell(builder, record.clientIp());
            appendCell(builder, String.valueOf(record.occurredAt()));
            appendCell(builder, String.valueOf(record.details()));
            builder.append('\n');
        }
        return builder.toString();
    }

    private void appendCell(StringBuilder builder, String value) {
        if (builder.charAt(builder.length() - 1) != '\n') {
            builder.append(',');
        }
        String normalized = value == null ? "" : value.replace("\"", "\"\"");
        builder.append('"').append(normalized).append('"');
    }
}
