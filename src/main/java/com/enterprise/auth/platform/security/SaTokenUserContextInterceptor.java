package com.enterprise.auth.platform.security;

import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.auth.service.SessionIndexService;
import com.enterprise.auth.platform.tenant.TenantContext;
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

    public SaTokenUserContextInterceptor(CurrentUserService currentUserService, SessionIndexService sessionIndexService) {
        this.currentUserService = currentUserService;
        this.sessionIndexService = sessionIndexService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (StpUtil.isLogin()) {
            var principal = currentUserService.bindRequestContext(request);
            enforceClientIpBinding(request);
            long lastAccessAt = Instant.now().toEpochMilli();
            StpUtil.getTokenSession().set("lastAccessAt", lastAccessAt);
            String token = StpUtil.getTokenValue();
            if (!sessionIndexService.touch(token, lastAccessAt)) {
                AuthContextHolder.currentUser().ifPresent(user -> sessionIndexService.register(
                        token,
                        user.id(),
                        user.username(),
                        user.tenantId(),
                        String.valueOf(StpUtil.getTokenSession().get("clientIp")),
                        String.valueOf(StpUtil.getTokenSession().get("device")),
                        sessionLong("issuedAt", lastAccessAt),
                        sessionLong("expiresAt", 0L)
                ));
                sessionIndexService.touch(token, lastAccessAt);
            }
            TenantContext.setTenantId(principal.tenantId());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContextHolder.clear();
    }

    private void enforceClientIpBinding(HttpServletRequest request) {
        Object boundClientIpValue = StpUtil.getTokenSession().get("clientIp");
        String boundClientIp = boundClientIpValue == null ? null : String.valueOf(boundClientIpValue);
        String requestClientIp = clientIp(request);
        if (StringUtils.hasText(boundClientIp) && StringUtils.hasText(requestClientIp) && !boundClientIp.equals(requestClientIp)) {
            String token = StpUtil.getTokenValue();
            StpUtil.kickoutByTokenValue(token);
            sessionIndexService.remove(token);
            StpUtil.checkLogin();
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
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
