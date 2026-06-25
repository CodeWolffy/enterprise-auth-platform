package com.enterprise.auth.platform.modules.log.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.log.application.SysLogService;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLogEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/logs/operation")
public class SysLogController {

    private final SysLogService sysLogService;

    public SysLogController(SysLogService sysLogService) {
        this.sysLogService = sysLogService;
    }

    @Operation(summary = "操作日志列表")
    @GetMapping
    @SaCheckPermission(PermissionCodes.OPERATION_LOG_PAGE)
    public ApiResponse<PageResult<SysLogEntity>> page(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) Long fromEpochMs,
            @RequestParam(required = false) Long toEpochMs,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size
    ) {
        LocalDateTime from = fromEpochMs != null ? LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(fromEpochMs), ZoneId.of("UTC")) : null;
        LocalDateTime to = toEpochMs != null ? LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(toEpochMs), ZoneId.of("UTC")) : null;
        return ApiResponse.ok(sysLogService.page(tenantId, eventType, operator, requestId, clientIp, from, to, page, size));
    }
}