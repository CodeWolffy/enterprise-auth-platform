package com.enterprise.auth.platform.auth.controller;

import com.enterprise.auth.platform.auth.dto.CaptchaResponse;
import com.enterprise.auth.platform.auth.dto.LoginRequest;
import com.enterprise.auth.platform.auth.dto.PermissionSnapshotResponse;
import com.enterprise.auth.platform.auth.dto.RefreshTokenRequest;
import com.enterprise.auth.platform.auth.dto.TokenResponse;
import com.enterprise.auth.platform.auth.dto.UserSessionResponse;
import com.enterprise.auth.platform.auth.model.TokenClaims;
import com.enterprise.auth.platform.auth.service.AuthService;
import com.enterprise.auth.platform.auth.service.CaptchaService;
import com.enterprise.auth.platform.auth.service.PermissionSnapshotService;
import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.user.model.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证中心")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CaptchaService captchaService;
    private final AuthService authService;
    private final PermissionSnapshotService permissionSnapshotService;

    public AuthController(
            CaptchaService captchaService,
            AuthService authService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this.captchaService = captchaService;
        this.authService = authService;
        this.permissionSnapshotService = permissionSnapshotService;
    }

    @Operation(summary = "获取登录验证码")
    @GetMapping("/captcha")
    public ApiResponse<CaptchaResponse> captcha() {
        CaptchaService.CaptchaChallenge challenge = captchaService.create();
        return ApiResponse.ok(new CaptchaResponse(
                challenge.captchaId(),
                challenge.expiresAt(),
                challenge.previewCode()
        ));
    }

    @Operation(summary = "账号登录")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.login(request, servletRequest));
    }

    @Operation(summary = "刷新访问令牌")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {
        UserAccount user = currentUser(authentication);
        TokenClaims claims = currentClaims(authentication);
        authService.logout(claims.sessionId(), user.username(), user.tenantId());
        return ApiResponse.ok();
    }

    @Operation(summary = "获取当前用户权限快照")
    @GetMapping("/me")
    public ApiResponse<PermissionSnapshotResponse> me(Authentication authentication) {
        return ApiResponse.ok(permissionSnapshotService.build(currentUser(authentication)));
    }

    @Operation(summary = "获取当前用户在线会话")
    @GetMapping("/sessions")
    public ApiResponse<List<UserSessionResponse>> sessions(Authentication authentication) {
        return ApiResponse.ok(authService.sessions(currentUser(authentication)));
    }

    @Operation(summary = "强制指定会话下线")
    @PostMapping("/sessions/{sessionId}/offline")
    public ApiResponse<Void> forceOffline(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            Authentication authentication
    ) {
        authService.forceOffline(currentUser(authentication), sessionId);
        return ApiResponse.ok();
    }

    private UserAccount currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserAccount user)) {
            throw new BusinessException("用户未登录");
        }
        return user;
    }

    private TokenClaims currentClaims(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof TokenClaims claims)) {
            throw new BusinessException("缺少会话信息");
        }
        return claims;
    }
}
