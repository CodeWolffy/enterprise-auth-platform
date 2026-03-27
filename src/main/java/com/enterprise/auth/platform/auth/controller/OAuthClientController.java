package com.enterprise.auth.platform.auth.controller;

import com.enterprise.auth.platform.auth.dto.CreateOauthClientRequest;
import com.enterprise.auth.platform.auth.dto.OauthClientStatusRequest;
import com.enterprise.auth.platform.auth.dto.RotateOauthClientSecretRequest;
import com.enterprise.auth.platform.auth.dto.UpdateOauthClientRequest;
import com.enterprise.auth.platform.auth.service.OAuthClientManagementService;
import com.enterprise.auth.platform.common.annotation.RateLimit;
import com.enterprise.auth.platform.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OAuth2 客户端管理")
@RestController
@RequestMapping("/api/oauth-clients")
public class OAuthClientController {

    private final OAuthClientManagementService clientManagementService;

    public OAuthClientController(OAuthClientManagementService clientManagementService) {
        this.clientManagementService = clientManagementService;
    }

    @Operation(summary = "查询 OAuth2 客户端列表")
    @GetMapping
    @PreAuthorize("hasAuthority('auth:read')")
    public ApiResponse<List<OAuthClientManagementService.OAuthClientView>> clients() {
        return ApiResponse.ok(clientManagementService.clients());
    }

    @Operation(summary = "查询 OAuth2 客户端详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:read')")
    public ApiResponse<OAuthClientManagementService.OAuthClientView> clientDetail(
            @Parameter(description = "客户端主键 ID") @PathVariable Long id
    ) {
        return ApiResponse.ok(clientManagementService.clientDetail(id));
    }

    @Operation(summary = "客户端-作用域联动引导")
    @GetMapping("/{id}/scope-linkage")
    @PreAuthorize("hasAuthority('auth:read')")
    public ApiResponse<OAuthClientManagementService.OAuthClientScopeLinkageView> scopeLinkage(
            @Parameter(description = "客户端主键 ID") @PathVariable Long id
    ) {
        return ApiResponse.ok(clientManagementService.scopeLinkage(id));
    }

    @Operation(summary = "新增 OAuth2 客户端")
    @PostMapping
    @PreAuthorize("hasAuthority('auth:write')")
    public ApiResponse<OAuthClientManagementService.OAuthClientView> createClient(
            @Valid @RequestBody CreateOauthClientRequest request
    ) {
        return ApiResponse.ok(clientManagementService.createClient(request));
    }

    @Operation(summary = "修改 OAuth2 客户端")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:write')")
    public ApiResponse<OAuthClientManagementService.OAuthClientView> updateClient(
            @Parameter(description = "客户端主键 ID") @PathVariable Long id,
            @Valid @RequestBody UpdateOauthClientRequest request
    ) {
        return ApiResponse.ok(clientManagementService.updateClient(id, request));
    }

    @Operation(summary = "启用或禁用 OAuth2 客户端")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('auth:write')")
    public ApiResponse<OAuthClientManagementService.OAuthClientView> updateClientStatus(
            @Parameter(description = "客户端主键 ID") @PathVariable Long id,
            @Valid @RequestBody OauthClientStatusRequest request
    ) {
        return ApiResponse.ok(clientManagementService.updateClientStatus(id, request.enabled()));
    }

    @Operation(summary = "轮换 OAuth2 客户端密钥")
    @RateLimit(key = "rotate-secret", strategy = RateLimit.Strategy.USER)
    @PostMapping("/{id}/rotate-secret")
    @PreAuthorize("hasAuthority('auth:write')")
    public ApiResponse<OAuthClientManagementService.OAuthClientView> rotateClientSecret(
            @Parameter(description = "客户端主键 ID") @PathVariable Long id,
            @Valid @RequestBody RotateOauthClientSecretRequest request
    ) {
        return ApiResponse.ok(clientManagementService.rotateClientSecret(id, request));
    }

    @Operation(summary = "删除 OAuth2 客户端")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:write')")
    public ApiResponse<Void> deleteClient(@Parameter(description = "客户端主键 ID") @PathVariable Long id) {
        clientManagementService.deleteClient(id);
        return ApiResponse.ok();
    }
}
