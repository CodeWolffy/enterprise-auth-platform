package com.enterprise.auth.platform.infrastructure.security;

import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.modules.auth.domain.AuthContextHolder;
import com.enterprise.auth.platform.common.context.RequestLogContext;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.ClientIpResolver;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SaTokenUserContextInterceptor implements HandlerInterceptor {

    private final CurrentUserService currentUserService;
    private final SessionIndexService sessionIndexService;
    private final ClientIpResolver clientIpResolver;

    public SaTokenUserContextInterceptor(
            CurrentUserService currentUserService,
            SessionIndexService sessionIndexService,
            ClientIpResolver clientIpResolver
    ) {
        this.currentUserService = currentUserService;
        this.sessionIndexService = sessionIndexService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (StpUtil.isLogin()) {
            var principal = currentUserService.bindRequestContext(request);
            enforceClientIpBinding(request);
            long lastAccessAt = Instant.now().toEpochMilli();
            // Sa-Token 原生 lastAccess 仍每次写，保证 active-timeout 语义
            StpUtil.getTokenSession().set("lastAccessAt", lastAccessAt);
            String token = StpUtil.getTokenValue();
            boolean touched = sessionIndexService.touch(token, lastAccessAt);
            if (!touched) {
                AuthContextHolder.currentUser().ifPresent(user -> sessionIndexService.register(
                        token,
                        user.id(),
                        user.username(),
                        user.tenantId(),
                        String.valueOf(StpUtil.getTokenSession().get("clientIp")),
                        String.valueOf(StpUtil.getTokenSession().get("loginLocation")),
                        String.valueOf(StpUtil.getTokenSession().get("device")),
                        sessionLong("issuedAt", lastAccessAt),
                        sessionLong("expiresAt", 0L)
                ));
                sessionIndexService.touch(token, lastAccessAt);
            }
            // 仅在租户实际变化时写影子索引（Lua 内再做一次幂等比对）
            sessionIndexService.updateActiveTenant(token, principal.tenantId());
            if (principal.globalScope()) {
                TenantContext.setGlobalScope(principal.tenantId());
            } else {
                TenantContext.setTenantId(principal.tenantId());
            }
            AuthContextHolder.currentUser().ifPresent(user -> RequestLogContext.bindPrincipal(
                    user.id(), user.username(), principal.tenantId()));
            enforcePasswordChangeRestriction(request);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContextHolder.clear();
        TenantContext.clear();
        RequestLogContext.clearPrincipal();
    }

    private void enforceClientIpBinding(HttpServletRequest request) {
        Object boundClientIpValue = StpUtil.getTokenSession().get("clientIp");
        String boundClientIp = boundClientIpValue == null ? null : String.valueOf(boundClientIpValue);
        if (!StringUtils.hasText(boundClientIp) || "null".equals(boundClientIp)) {
            return;
        }
        String requestClientIp = clientIpResolver.resolve(request);
        if (StringUtils.hasText(requestClientIp) && !boundClientIp.equals(requestClientIp)) {
            String token = StpUtil.getTokenValue();
            StpUtil.kickoutByTokenValue(token);
            sessionIndexService.remove(token);
            StpUtil.checkLogin();
        }
    }

    private void enforcePasswordChangeRestriction(HttpServletRequest request) {
        Object requiredValue = StpUtil.getTokenSession().get("passwordChangeRequired");
        boolean required = requiredValue instanceof Boolean value ? value : Boolean.parseBoolean(String.valueOf(requiredValue));
        if (!required) {
            return;
        }
        String path = request.getRequestURI();
        if ("POST".equalsIgnoreCase(request.getMethod())
                && ("/api/account/password/change".equals(path) || "/api/auth/logout".equals(path))) {
            return;
        }
        throw new BusinessException("PASSWORD_CHANGE_REQUIRED", "当前会话必须先修改密码");
    }

    private long sessionLong(String key, long fallback) {
        Object value = StpUtil.getTokenSession().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}