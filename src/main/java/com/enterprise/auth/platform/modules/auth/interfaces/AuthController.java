package com.enterprise.auth.platform.modules.auth.interfaces;

import com.enterprise.auth.platform.modules.auth.interfaces.CaptchaVerifyRequest;
import com.enterprise.auth.platform.modules.auth.interfaces.LoginRequest;
import com.enterprise.auth.platform.modules.auth.interfaces.PermissionSnapshotResponse;
import com.enterprise.auth.platform.modules.auth.interfaces.RegisterOptionsResponse;
import com.enterprise.auth.platform.modules.auth.interfaces.TokenSessionResponse;
import com.enterprise.auth.platform.modules.auth.application.LoginApplicationService;
import com.enterprise.auth.platform.modules.auth.application.PasswordResetApplicationService;
import com.enterprise.auth.platform.modules.auth.application.PermissionSnapshotApplicationService;
import com.enterprise.auth.platform.modules.auth.application.RegistrationApplicationService;
import com.enterprise.auth.platform.modules.auth.application.SessionApplicationService;
import com.enterprise.auth.platform.modules.auth.application.TenantSwitchApplicationService;
import com.enterprise.auth.platform.modules.auth.application.CaptchaService;
import com.enterprise.auth.platform.modules.auth.application.RegistrationPolicyService;
import com.enterprise.auth.platform.common.web.RateLimit;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.context.AuthContextHolder;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.interfaces.RegisterRequest;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.user.interfaces.UserSummary;
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
    private final LoginApplicationService loginApplicationService;
    private final RegistrationApplicationService registrationApplicationService;
    private final SessionApplicationService sessionApplicationService;
    private final TenantSwitchApplicationService tenantSwitchApplicationService;
    private final PermissionSnapshotApplicationService permissionSnapshotApplicationService;
    private final RegistrationPolicyService registrationPolicyService;
    private final CurrentUserService currentUserService;
    private final PasswordResetApplicationService passwordResetApplicationService;

    public AuthController(
            CaptchaService captchaService,
            LoginApplicationService loginApplicationService,
            RegistrationApplicationService registrationApplicationService,
            SessionApplicationService sessionApplicationService,
            TenantSwitchApplicationService tenantSwitchApplicationService,
            PermissionSnapshotApplicationService permissionSnapshotApplicationService,
            RegistrationPolicyService registrationPolicyService,
            CurrentUserService currentUserService,
            PasswordResetApplicationService passwordResetApplicationService
    ) {
        this.captchaService = captchaService;
        this.loginApplicationService = loginApplicationService;
        this.registrationApplicationService = registrationApplicationService;
        this.sessionApplicationService = sessionApplicationService;
        this.tenantSwitchApplicationService = tenantSwitchApplicationService;
        this.permissionSnapshotApplicationService = permissionSnapshotApplicationService;
        this.registrationPolicyService = registrationPolicyService;
        this.currentUserService = currentUserService;
        this.passwordResetApplicationService = passwordResetApplicationService;
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
        return ApiResponse.ok(loginApplicationService.login(request, servletRequest));
    }

    @Operation(summary = "发起忘记密码")
    @RateLimit(key = "password-reset-request", strategy = RateLimit.Strategy.IP)
    @PostMapping("/password/reset/request")
    public ApiResponse<PasswordResetApplicationService.PasswordResetRequestResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetApplicationService.PasswordResetRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(passwordResetApplicationService.request(request, servletRequest));
    }

    @Operation(summary = "校验密码重置令牌")
    @PostMapping("/password/reset/verify")
    public ApiResponse<PasswordResetApplicationService.PasswordResetVerifyResponse> verifyPasswordReset(
            @Valid @RequestBody PasswordResetApplicationService.PasswordResetVerifyRequest request
    ) {
        return ApiResponse.ok(passwordResetApplicationService.verify(request));
    }

    @Operation(summary = "确认重置密码")
    @PostMapping("/password/reset/confirm")
    public ApiResponse<PasswordResetApplicationService.PasswordResetConfirmResponse> confirmPasswordReset(
            @Valid @RequestBody PasswordResetApplicationService.PasswordResetConfirmRequest request
    ) {
        return ApiResponse.ok(passwordResetApplicationService.confirm(request));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        UserAccount user = currentUser();
        AuthContextHolder.currentSession()
                .ifPresent(session -> sessionApplicationService.logout(session.sessionId(), user.username(), user.tenantId()));
        return ApiResponse.ok();
    }

    @Operation(summary = "获取当前用户权限快照")
    @GetMapping("/me")
    public ApiResponse<PermissionSnapshotResponse> me() {
        return ApiResponse.ok(permissionSnapshotApplicationService.build(currentUser()));
    }

    @Operation(summary = "切换当前会话活跃租户")
    @PostMapping("/tenants/{tenantId}/switch")
    public ApiResponse<PermissionSnapshotResponse> switchTenant(
            @Parameter(description = "目标租户ID") @PathVariable String tenantId
    ) {
        return ApiResponse.ok(tenantSwitchApplicationService.switchTenant(currentUser(), tenantId));
    }

  @Operation(summary = "获取当前用户在线会话")
  @GetMapping("/sessions")
  public ApiResponse<Object> sessions(
      @Parameter(description = "查询范围: own(仅自己) 或 all(全租户)") @RequestParam(defaultValue = "own") String scope,
      @Parameter(description = "页码，scope=all 时生效") @RequestParam(required = false) Integer page,
      @Parameter(description = "每页数量，scope=all 时生效") @RequestParam(required = false) Integer size
  ) {
    if ("all".equals(scope)) {
      return ApiResponse.ok(sessionApplicationService.sessions(currentUser(), scope, StpUtil.getTokenValue(), page, size));
    }
    return ApiResponse.ok(sessionApplicationService.sessions(currentUser(), scope, StpUtil.getTokenValue(), page, size).records());
  }

    @Operation(summary = "强制指定会话下线")
    @PostMapping("/sessions/{sessionId}/offline")
    public ApiResponse<Void> forceOffline(
            @Parameter(description = "会话ID") @PathVariable String sessionId
    ) {
        sessionApplicationService.forceOffline(currentUser(), sessionId);
        return ApiResponse.ok();
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public ApiResponse<UserSummary> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(registrationApplicationService.register(request, servletRequest));
    }

    private UserAccount currentUser() {
        return currentUserService.requireCurrentUser();
    }
}
