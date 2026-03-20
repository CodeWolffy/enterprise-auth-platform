package com.enterprise.auth.platform.auth.controller;

import com.enterprise.auth.platform.auth.dto.ConsentView;
import com.enterprise.auth.platform.auth.service.OauthConsentService;
import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.common.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "授权同意记录")
@RestController
@RequestMapping("/api/auth/consents")
public class OauthConsentController {

    private final OauthConsentService oauthConsentService;

    public OauthConsentController(OauthConsentService oauthConsentService) {
        this.oauthConsentService = oauthConsentService;
    }

    @Operation(summary = "分页查询授权同意记录")
    @GetMapping
    @PreAuthorize("hasAuthority('auth:read')")
    public ApiResponse<PageResult<ConsentView>> queryConsents(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "客户端 Client ID，支持模糊匹配") @RequestParam(required = false) String clientId,
            @Parameter(description = "授权主体用户名，支持模糊匹配") @RequestParam(required = false) String principalName
    ) {
        return ApiResponse.ok(oauthConsentService.queryConsents(page, size, clientId, principalName));
    }

    @Operation(summary = "撤销授权同意记录")
    @DeleteMapping
    @PreAuthorize("hasAuthority('auth:write')")
    public ApiResponse<Void> revokeConsent(
            @Parameter(description = "授权中心内部注册客户端 ID") @RequestParam String registeredClientId,
            @Parameter(description = "授权主体用户名") @RequestParam String principalName
    ) {
        oauthConsentService.revokeConsent(registeredClientId, principalName);
        return ApiResponse.ok();
    }
}
