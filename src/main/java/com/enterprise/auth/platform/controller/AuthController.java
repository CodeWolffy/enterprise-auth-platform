package com.enterprise.auth.platform.controller;

import com.enterprise.auth.platform.dto.req.CaptchaVerifyRequest;
import com.enterprise.auth.platform.dto.req.LoginRequest;
import com.enterprise.auth.platform.dto.resp.PermissionSnapshotResponse;
import com.enterprise.auth.platform.dto.resp.RegisterOptionsResponse;
import com.enterprise.auth.platform.dto.resp.TokenSessionResponse;
import com.enterprise.auth.platform.dto.resp.UserSessionResponse;
import com.enterprise.auth.platform.service.AuthService;
import com.enterprise.auth.platform.service.CaptchaService;
import com.enterprise.auth.platform.service.PermissionSnapshotService;
import com.enterprise.auth.platform.service.RegistrationPolicyService;
import com.enterprise.auth.platform.common.web.RateLimit;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.dto.model.PageResult;
import com.enterprise.auth.platform.common.context.AuthContextHolder;
import com.enterprise.auth.platform.security.CurrentUserService;
import com.enterprise.auth.platform.dto.req.RegisterRequest;
import com.enterprise.auth.platform.dto.model.UserAccount;
import com.enterprise.auth.platform.dto.resp.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证中心")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CaptchaService captchaService;
    private final AuthService authService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final RegistrationPolicyService registrationPolicyService;
    private final CurrentUserService currentUserService;

    public AuthController(
            CaptchaService captchaService,
            AuthService authService,
            PermissionSnapshotService permissionSnapshotService,
            RegistrationPolicyService registrationPolicyService,
            CurrentUserService currentUserService
    ) {
        this.captchaService = captchaService;
        this.authService = authService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.registrationPolicyService = registrationPolicyService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "获取登录验证码")
    @RateLimit(key = "captcha", strategy = RateLimit.Strategy.IP)
    @GetMapping("/captcha")
    public ApiResponse<CaptchaService.CaptchaChallenge> captcha() {
        return ApiResponse.ok(captchaService.create());
    }

    @Operation(summary = "校验滑块验证码")
    @RateLimit(key = "captcha-verify", strategy = RateLimit.Strategy.IP)
    @PostMapping("/captcha/verify")
    public ApiResponse<Void> verifyCaptcha(@Valid @RequestBody CaptchaVerifyRequest request) {
        captchaService.verify(request.captchaId(), request.captchaCode());
        return ApiResponse.ok();
    }

    @Operation(summary = "获取注册默认配置")
    @GetMapping("/register/options")
    public ApiResponse<RegisterOptionsResponse> registerOptions() {
        return ApiResponse.ok(new RegisterOptionsResponse(
                registrationPolicyService.resolveDefaultTenantId(),
                List.copyOf(registrationPolicyService.resolveDefaultRoleCodes())
        ));
    }

    @Operation(summary = "账号密码登录并返回 Sa-Token Bearer Token")
    @RateLimit(key = "login", strategy = RateLimit.Strategy.IP)
    @PostMapping("/login")
    public ApiResponse<TokenSessionResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(authService.login(request, servletRequest));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        UserAccount user = currentUser();
        AuthContextHolder.currentSession()
                .ifPresent(session -> authService.logout(session.sessionId(), user.username(), user.tenantId()));
        return ApiResponse.ok();
    }

    @Operation(summary = "获取当前用户权限快照")
    @GetMapping("/me")
    public ApiResponse<PermissionSnapshotResponse> me() {
        return ApiResponse.ok(permissionSnapshotService.build(currentUser()));
    }

    @Operation(summary = "切换当前会话活跃租户")
    @PostMapping("/tenants/{tenantId}/switch")
    public ApiResponse<PermissionSnapshotResponse> switchTenant(
            @Parameter(description = "目标租户ID") @PathVariable String tenantId
    ) {
        return ApiResponse.ok(authService.switchTenant(currentUser(), tenantId));
    }

  @Operation(summary = "获取当前用户在线会话")
  @GetMapping("/sessions")
  public ApiResponse<Object> sessions(
      @Parameter(description = "查询范围: own(仅自己) 或 all(全租户)") @RequestParam(defaultValue = "own") String scope,
      @Parameter(description = "页码，scope=all 时生效") @RequestParam(required = false) Integer page,
      @Parameter(description = "每页数量，scope=all 时生效") @RequestParam(required = false) Integer size
  ) {
    if ("all".equals(scope)) {
      return ApiResponse.ok(authService.sessions(currentUser(), scope, StpUtil.getTokenValue(), page, size));
    }
    return ApiResponse.ok(authService.sessions(currentUser(), scope, StpUtil.getTokenValue(), page, size).records());
  }

    @Operation(summary = "强制指定会话下线")
    @PostMapping("/sessions/{sessionId}/offline")
    public ApiResponse<Void> forceOffline(
            @Parameter(description = "会话ID") @PathVariable String sessionId
    ) {
        authService.forceOffline(currentUser(), sessionId);
        return ApiResponse.ok();
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public ApiResponse<UserSummary> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.register(request, servletRequest));
    }

    private UserAccount currentUser() {
        return currentUserService.requireCurrentUser();
    }
}
