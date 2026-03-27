package com.enterprise.auth.platform.auth.controller;

import com.enterprise.auth.platform.auth.dto.CaptchaResponse;
import com.enterprise.auth.platform.auth.dto.CookieSessionResponse;
import com.enterprise.auth.platform.auth.dto.CsrfTokenResponse;
import com.enterprise.auth.platform.auth.dto.LoginRequest;
import com.enterprise.auth.platform.auth.dto.OAuthCodeExchangeRequest;
import com.enterprise.auth.platform.auth.dto.PermissionSnapshotResponse;
import com.enterprise.auth.platform.auth.dto.RegisterOptionsResponse;
import com.enterprise.auth.platform.auth.dto.RefreshTokenRequest;
import com.enterprise.auth.platform.auth.dto.TokenResponse;
import com.enterprise.auth.platform.auth.dto.UserSessionResponse;
import com.enterprise.auth.platform.auth.model.TokenClaims;
import com.enterprise.auth.platform.auth.security.TrustedRequestOriginValidator;
import com.enterprise.auth.platform.auth.service.AuthService;
import com.enterprise.auth.platform.auth.service.CaptchaService;
import com.enterprise.auth.platform.auth.service.OAuthCookieSessionService;
import com.enterprise.auth.platform.auth.service.PermissionSnapshotService;
import com.enterprise.auth.platform.auth.service.RegistrationPolicyService;
import com.enterprise.auth.platform.common.annotation.RateLimit;
import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.time.TimeSupport;
import com.enterprise.auth.platform.user.dto.RegisterRequest;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.model.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
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
    private final OAuthCookieSessionService oauthCookieSessionService;
    private final TrustedRequestOriginValidator trustedRequestOriginValidator;
    private final RegistrationPolicyService registrationPolicyService;

    public AuthController(
            CaptchaService captchaService,
            AuthService authService,
            PermissionSnapshotService permissionSnapshotService,
            OAuthCookieSessionService oauthCookieSessionService,
            TrustedRequestOriginValidator trustedRequestOriginValidator,
            RegistrationPolicyService registrationPolicyService
    ) {
        this.captchaService = captchaService;
        this.authService = authService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.oauthCookieSessionService = oauthCookieSessionService;
        this.trustedRequestOriginValidator = trustedRequestOriginValidator;
        this.registrationPolicyService = registrationPolicyService;
    }

    @Operation(summary = "获取登录验证码")
    @RateLimit(key = "captcha", strategy = RateLimit.Strategy.IP)
    @GetMapping("/captcha")
    public ApiResponse<CaptchaResponse> captcha() {
        CaptchaService.CaptchaChallenge challenge = captchaService.create();
        return ApiResponse.ok(new CaptchaResponse(
                challenge.captchaId(),
                TimeSupport.toEpochMilli(challenge.expiresAt()),
                challenge.previewCode()
        ));
    }

    @Operation(summary = "获取 CSRF Token")
    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        return ApiResponse.ok(new CsrfTokenResponse(
                csrfToken.getHeaderName(),
                csrfToken.getParameterName(),
                csrfToken.getToken()
        ));
    }

    @Operation(summary = "获取注册公共配置")
    @GetMapping("/register/options")
    public ApiResponse<RegisterOptionsResponse> registerOptions() {
        return ApiResponse.ok(new RegisterOptionsResponse(
                registrationPolicyService.resolveDefaultTenantId(),
                List.copyOf(registrationPolicyService.resolveDefaultRoleCodes())
        ));
    }

    @Operation(summary = "OAuth 授权码交换并写入安全 Cookie")
    @RateLimit(key = "oauth", strategy = RateLimit.Strategy.IP)
    @PostMapping("/oauth/exchange")
    public ApiResponse<CookieSessionResponse> oauthExchange(
            @Valid @RequestBody OAuthCodeExchangeRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        trustedRequestOriginValidator.requireTrustedBrowserOrigin(servletRequest);
        return ApiResponse.ok(oauthCookieSessionService.exchangeAuthorizationCode(
                request.code(),
                request.codeVerifier(),
                request.redirectUri(),
                servletRequest,
                servletResponse
        ));
    }

    @Operation(summary = "使用 Cookie 刷新会话")
    @RateLimit(key = "oauth", strategy = RateLimit.Strategy.IP)
    @PostMapping("/oauth/refresh")
    public ApiResponse<CookieSessionResponse> oauthRefresh(
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        trustedRequestOriginValidator.requireTrustedBrowserOrigin(servletRequest);
        return ApiResponse.ok(oauthCookieSessionService.refresh(servletRequest, servletResponse));
    }

    @Operation(summary = "账号登录")
    @RateLimit(key = "login", strategy = RateLimit.Strategy.IP)
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.login(request, servletRequest));
    }

    @Operation(summary = "刷新访问令牌")
    @RateLimit(key = "refresh", strategy = RateLimit.Strategy.IP)
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            Authentication authentication,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        trustedRequestOriginValidator.requireTrustedBrowserOrigin(servletRequest);
        oauthCookieSessionService.clearCookies(servletRequest, servletResponse);
        if (authentication != null
                && authentication.getPrincipal() instanceof UserAccount user
                && authentication.getDetails() instanceof TokenClaims claims) {
            authService.logout(claims.sessionId(), user.username(), user.tenantId());
        }
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
            @Parameter(description = "会话 ID") @PathVariable String sessionId,
            Authentication authentication
    ) {
        authService.forceOffline(currentUser(authentication), sessionId);
        return ApiResponse.ok();
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public ApiResponse<UserSummary> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.register(request, servletRequest));
    }

    private UserAccount currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserAccount user)) {
            throw new BusinessException("用户未登录");
        }
        return user;
    }
}
