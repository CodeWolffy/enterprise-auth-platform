package com.enterprise.auth.platform.modules.security.interfaces;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.audit.SysLog;
import com.enterprise.auth.platform.modules.security.application.SecurityPolicyApplicationService;
import com.enterprise.auth.platform.modules.security.application.SecurityPolicyView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "安全策略")
@RestController
@RequestMapping("/api/security/policy")
public class SecurityPolicyController {

    private final SecurityPolicyApplicationService securityPolicyApplicationService;

    public SecurityPolicyController(SecurityPolicyApplicationService securityPolicyApplicationService) {
        this.securityPolicyApplicationService = securityPolicyApplicationService;
    }

    @Operation(summary = "查询当前租户密码策略")
    @GetMapping("/password-policy")
    public ApiResponse<SecurityPolicyView> passwordPolicy() {
        return ApiResponse.ok(securityPolicyApplicationService.currentTenantPolicyView());
    }

    @Operation(summary = "查询当前租户生效安全策略")
    @GetMapping
    @SaCheckPermission(PermissionCodes.SECURITY_GET)
    public ApiResponse<SecurityPolicyView> currentTenantPolicy() {
        return ApiResponse.ok(securityPolicyApplicationService.currentTenantPolicyView());
    }

    @SysLog("更新当前租户安全策略覆盖")
    @Operation(summary = "更新当前租户安全策略覆盖")
    @PutMapping
    @SaCheckPermission(PermissionCodes.SECURITY_EDIT)
    public ApiResponse<SecurityPolicyView> updateTenantPolicy(@Valid @RequestBody SecurityPolicyRequest request) {
        return ApiResponse.ok(securityPolicyApplicationService.updateTenantPolicy(request));
    }

    @Operation(summary = "查询平台默认安全策略")
    @GetMapping("/platform")
    @SaCheckPermission(PermissionCodes.SECURITY_GET)
    public ApiResponse<SecurityPolicyView> platformPolicy() {
        return ApiResponse.ok(securityPolicyApplicationService.platformPolicyView());
    }

    @SysLog("更新平台默认安全策略")
    @Operation(summary = "更新平台默认安全策略")
    @PutMapping("/platform")
    @SaCheckPermission(PermissionCodes.SECURITY_EDIT)
    public ApiResponse<SecurityPolicyView> updatePlatformPolicy(@Valid @RequestBody SecurityPolicyRequest request) {
        return ApiResponse.ok(securityPolicyApplicationService.updatePlatformPolicy(request));
    }
}
