package com.enterprise.auth.platform.controller;

import com.alibaba.excel.EasyExcel;
import com.enterprise.auth.platform.dto.req.AuditExportPolicyRequest;
import com.enterprise.auth.platform.dto.model.AuditEvent;
import com.enterprise.auth.platform.dto.resp.AuditExportVO;
import com.enterprise.auth.platform.dto.resp.AuditPage;
import com.enterprise.auth.platform.dto.req.AuditQuery;
import com.enterprise.auth.platform.service.AuditExportTaskService;
import com.enterprise.auth.platform.service.AuditService;
import com.enterprise.auth.platform.common.web.RateLimit;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.dto.model.PageResult;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.SecuritySupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "安全审计")
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private static final DateTimeFormatter EXPORT_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final AuditService auditService;
    private final AuditExportTaskService auditExportTaskService;

    public AuditController(AuditService auditService, AuditExportTaskService auditExportTaskService) {
        this.auditService = auditService;
        this.auditExportTaskService = auditExportTaskService;
    }

    @Operation(summary = "分页查询审计事件")
    @GetMapping("/events")
    @SaCheckPermission("audit:read")
    public ApiResponse<AuditPage> events(
            @Parameter(description = "租户编码") @RequestParam(required = false) String tenantId,
            @Parameter(description = "事件类型") @RequestParam(required = false) String eventType,
            @Parameter(description = "操作人") @RequestParam(required = false) String operator,
            @Parameter(description = "请求 ID") @RequestParam(required = false) String requestId,
            @Parameter(description = "客户端 IP") @RequestParam(required = false) String clientIp,
            @Parameter(description = "开始时间，epoch 毫秒，含边界") @RequestParam(required = false) Long fromEpochMs,
            @Parameter(description = "结束时间，epoch 毫秒，不含边界") @RequestParam(required = false) Long toEpochMs,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(auditService.query(buildQuery(
                tenantId, eventType, operator, requestId, clientIp, fromEpochMs, toEpochMs, page, size
        )));
    }

    @Operation(summary = "导出审计事件")
    @RateLimit(key = "export", strategy = RateLimit.Strategy.USER)
    @GetMapping("/events/export")
    @SaCheckPermission("audit:write")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) Long fromEpochMs,
            @RequestParam(required = false) Long toEpochMs
    ) {
        AuditQuery query = buildQuery(tenantId, eventType, operator, requestId, clientIp, fromEpochMs, toEpochMs, 1, 2000);
        List<AuditEvent> records = auditService.export(query);
        auditService.record(
                "AUDIT_EXPORTED",
                SecuritySupport.currentOperator(),
                StringUtils.hasText(tenantId) ? tenantId : "platform",
                Map.of(
                        "eventType", eventType == null ? "" : eventType,
                        "operator", operator == null ? "" : operator,
                        "clientIp", clientIp == null ? "" : clientIp,
                        "requestId", requestId == null ? "" : requestId,
                        "fromEpochMs", fromEpochMs == null ? "" : fromEpochMs,
                        "toEpochMs", toEpochMs == null ? "" : toEpochMs,
                        "recordCount", records.size()
                )
        );

        List<AuditExportVO> voList = records.stream().map(event -> new AuditExportVO(
                event.type(),
                event.operator(),
                event.tenantId(),
                event.requestId(),
                event.clientIp(),
                event.occurredAt() == null ? "" : EXPORT_FORMATTER.format(Instant.ofEpochMilli(event.occurredAt())),
                event.details() == null ? "{}" : event.details().toString()
        )).toList();

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        EasyExcel.write(os, AuditExportVO.class).sheet("审计日志").doWrite(voList);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-events.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(os.toByteArray());
    }

    @Operation(summary = "创建异步审计导出任务")
    @RateLimit(key = "export", strategy = RateLimit.Strategy.USER)
    @PostMapping("/exports")
    @SaCheckPermission("audit:write")
    public ApiResponse<AuditExportTaskService.ExportTaskView> createExportTask(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) Long fromEpochMs,
            @RequestParam(required = false) Long toEpochMs
    ) {
        return ApiResponse.ok(auditExportTaskService.create(buildQuery(
                tenantId, eventType, operator, requestId, clientIp, fromEpochMs, toEpochMs, 1, 2000
        )));
    }

    @Operation(summary = "分页查询审计导出任务")
    @GetMapping("/exports")
    @SaCheckPermission("audit:read")
    public ApiResponse<PageResult<AuditExportTaskService.ExportTaskView>> exportTasks(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String operator,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(auditExportTaskService.page(tenantId, status, operator, page, size));
    }

    @Operation(summary = "查询审计导出保留策略")
    @GetMapping("/exports/policy")
    @SaCheckPermission("audit:read")
    public ApiResponse<AuditExportTaskService.ExportPolicy> exportPolicy(
            @RequestParam(required = false) String tenantId
    ) {
        return ApiResponse.ok(auditExportTaskService.policy(tenantId));
    }

    @Operation(summary = "更新审计导出保留策略")
    @PutMapping("/exports/policy")
    @SaCheckPermission("audit:write")
    public ApiResponse<AuditExportTaskService.ExportPolicy> updateExportPolicy(
            @RequestParam(required = false) String tenantId,
            @RequestBody @jakarta.validation.Valid AuditExportPolicyRequest request
    ) {
        return ApiResponse.ok(auditExportTaskService.updatePolicy(tenantId, request));
    }

    @Operation(summary = "按策略执行审计导出治理")
    @PostMapping("/exports/governance")
    @SaCheckPermission("audit:write")
    public ApiResponse<AuditExportTaskService.GovernanceResult> governExportTasks(
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "false") boolean dryRun
    ) {
        return ApiResponse.ok(auditExportTaskService.governance(tenantId, dryRun));
    }

    @Operation(summary = "下载异步审计导出文件")
    @GetMapping("/exports/{taskId}/download")
    @SaCheckPermission("audit:write")
    public ResponseEntity<byte[]> downloadExportTask(@PathVariable Long taskId) {
        AuditExportTaskService.DownloadFile file = auditExportTaskService.download(taskId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.fileName())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.content());
    }

    @Operation(summary = "归档单条异步审计导出任务")
    @PostMapping("/exports/{taskId}/archive")
    @SaCheckPermission("audit:write")
    public ApiResponse<AuditExportTaskService.ExportTaskView> archiveExportTask(@PathVariable Long taskId) {
        return ApiResponse.ok(auditExportTaskService.archive(taskId));
    }

    @Operation(summary = "批量归档异步审计导出任务")
    @PostMapping("/exports/archive")
    @SaCheckPermission("audit:write")
    public ApiResponse<Integer> archiveExportTasks(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam Long completedBeforeEpochMs
    ) {
        return ApiResponse.ok(auditExportTaskService.archiveCompleted(tenantId, status, completedBeforeEpochMs));
    }

    @Operation(summary = "删除异步审计导出任务")
    @DeleteMapping("/exports/{taskId}")
    @SaCheckPermission("audit:write")
    public ApiResponse<Void> deleteExportTask(@PathVariable Long taskId) {
        auditExportTaskService.deleteTask(taskId);
        return ApiResponse.ok();
    }

    @Operation(summary = "重试异步审计导出任务")
    @RateLimit(key = "export", strategy = RateLimit.Strategy.USER)
    @PostMapping("/exports/{taskId}/retry")
    @SaCheckPermission("audit:write")
    public ApiResponse<AuditExportTaskService.ExportTaskView> retryExportTask(@PathVariable Long taskId) {
        return ApiResponse.ok(auditExportTaskService.retry(taskId));
    }

    @Operation(summary = "清理异步审计导出任务")
    @DeleteMapping("/exports")
    @SaCheckPermission("audit:write")
    public ApiResponse<Integer> cleanupExportTasks(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam Long completedBeforeEpochMs
    ) {
        return ApiResponse.ok(auditExportTaskService.cleanup(tenantId, status, completedBeforeEpochMs));
    }

    private AuditQuery buildQuery(
            String tenantId,
            String eventType,
            String operator,
            String requestId,
            String clientIp,
            Long fromEpochMs,
            Long toEpochMs,
            int page,
            int size
    ) {
        return new AuditQuery(tenantId, eventType, operator, requestId, clientIp, fromEpochMs, toEpochMs, page, size);
    }
}
