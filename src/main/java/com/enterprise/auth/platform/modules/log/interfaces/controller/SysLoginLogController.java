package com.enterprise.auth.platform.modules.log.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.log.application.SysLoginLogService;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLoginLogEntity;
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

@Tag(name = "登录日志")
@RestController
@RequestMapping("/api/logs/login")
public class SysLoginLogController {

    private final SysLoginLogService sysLoginLogService;

    public SysLoginLogController(SysLoginLogService sysLoginLogService) {
        this.sysLoginLogService = sysLoginLogService;
    }

    @Operation(summary = "登录日志列表")
    @GetMapping
    @SaCheckPermission(PermissionCodes.LOGIN_LOG_PAGE)
    public ApiResponse<PageResult<SysLoginLogEntity>> page(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) Long fromEpochMs,
            @RequestParam(required = false) Long toEpochMs,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size
    ) {
        LocalDateTime from = fromEpochMs != null ? LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(fromEpochMs), ZoneId.of("UTC")) : null;
        LocalDateTime to = toEpochMs != null ? LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(toEpochMs), ZoneId.of("UTC")) : null;
        return ApiResponse.ok(sysLoginLogService.page(tenantId, userName, status, clientIp, from, to, page, size));
    }
}