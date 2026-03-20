package com.enterprise.auth.platform.auth.controller;

import com.enterprise.auth.platform.auth.dto.OauthScopeCrudRequest;
import com.enterprise.auth.platform.auth.service.OAuthScopeManagementService;
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

@Tag(name = "OAuth2 作用域管理")
@RestController
@RequestMapping("/api/oauth-scopes")
public class OAuthScopeController {

    private final OAuthScopeManagementService oauthScopeManagementService;

    public OAuthScopeController(OAuthScopeManagementService oauthScopeManagementService) {
        this.oauthScopeManagementService = oauthScopeManagementService;
    }

    @Operation(summary = "查询 OAuth2 作用域列表")
    @GetMapping
    @PreAuthorize("hasAuthority('auth:read')")
    public ApiResponse<List<OAuthScopeManagementService.OAuthScopeView>> scopes() {
        return ApiResponse.ok(oauthScopeManagementService.scopes());
    }

    @Operation(summary = "新增 OAuth2 作用域")
    @PostMapping
    @PreAuthorize("hasAuthority('auth:write')")
    public ApiResponse<OAuthScopeManagementService.OAuthScopeView> createScope(@Valid @RequestBody OauthScopeCrudRequest request) {
        return ApiResponse.ok(oauthScopeManagementService.createScope(request));
    }

    @Operation(summary = "修改 OAuth2 作用域")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:write')")
    public ApiResponse<OAuthScopeManagementService.OAuthScopeView> updateScope(
            @Parameter(description = "作用域主键 ID") @PathVariable Long id,
            @Valid @RequestBody OauthScopeCrudRequest request
    ) {
        return ApiResponse.ok(oauthScopeManagementService.updateScope(id, request));
    }

    @Operation(summary = "删除 OAuth2 作用域")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('auth:write')")
    public ApiResponse<Void> deleteScope(@Parameter(description = "作用域主键 ID") @PathVariable Long id) {
        oauthScopeManagementService.deleteScope(id);
        return ApiResponse.ok();
    }
}
