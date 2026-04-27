package com.enterprise.auth.platform.security;

import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SaTokenUserContextInterceptor implements HandlerInterceptor {

    private final CurrentUserService currentUserService;

    public SaTokenUserContextInterceptor(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (StpUtil.isLogin()) {
            var principal = currentUserService.bindRequestContext(request);
            StpUtil.getTokenSession().set("lastAccessAt", Instant.now().toEpochMilli());
            TenantContext.setTenantId(principal.tenantId());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContextHolder.clear();
    }
}
