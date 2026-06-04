package com.enterprise.auth.platform.modules.audit.interfaces;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.RateLimit;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import com.enterprise.auth.platform.modules.audit.application.OperationLogCsvExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {

    private final AuditService auditService;
    private final OperationLogCsvExportService operationLogCsvExportService;

    public OperationLogController(
            AuditService auditService,
            OperationLogCsvExportService operationLogCsvExportService
    ) {
        this.auditService = auditService;
        this.operationLogCsvExportService = operationLogCsvExportService;
    }

    @Operation(summary = "分页查询操作日志")
    @GetMapping
    @SaCheckPermission(PermissionCodes.OPERATION_LOG_READ)
    public ApiResponse<AuditPage> page(
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

    @Operation(summary = "导出操作日志 CSV")
    @RateLimit(key = "operation-log-export", strategy = RateLimit.Strategy.USER)
    @GetMapping("/export")
    @SaCheckPermission(PermissionCodes.OPERATION_LOG_EXPORT)
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) Long fromEpochMs,
            @RequestParam(required = false) Long toEpochMs
    ) {
        byte[] content = operationLogCsvExportService.export(buildQuery(
                tenantId, eventType, operator, requestId, clientIp, fromEpochMs, toEpochMs, 1, 2000
        ));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=operation-logs.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(content);
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