package com.enterprise.auth.platform.test;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.modules.auth.domain.SessionPrincipal;
import com.enterprise.auth.platform.common.context.AuthContextHolder;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.user.application.AuthenticationUser;
import java.util.List;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class SaTokenMockMvcSupport {

    private SaTokenMockMvcSupport() {
    }

    public static RequestPostProcessor bearer(UserAccount user) {
        return bearer(user, user.tenantId());
    }

    public static RequestPostProcessor bearer(UserAccount user, String activeTenantId) {
        return request -> {
            String token = StpUtil.createLoginSession(user.id(), new SaLoginModel().setDevice("mockmvc"));
            SaSession tokenSession = StpUtil.getTokenSessionByToken(token);
            tokenSession.set("username", user.username());
            tokenSession.set("userId", user.id());
            tokenSession.set("tenantId", user.tenantId());
            tokenSession.set("activeTenantId", activeTenantId);
            tokenSession.set("sessionVersion", user.sessionVersion());
            tokenSession.set("roles", List.copyOf(user.roles()));
            tokenSession.set("permissions", List.copyOf(user.permissions()));
            tokenSession.set("clientIp", "127.0.0.1");
            tokenSession.set("device", "mockmvc");
            long now = System.currentTimeMillis();
            tokenSession.set("issuedAt", now);
            tokenSession.set("expiresAt", now + 7 * 24 * 60 * 60 * 1000L);
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    public static void bind(UserAccount user) {
        AuthContextHolder.set(user, new SessionPrincipal("test-token", user.tenantId(), user.tenantId()));
    }

    public static void bind(AuthenticationUser user) {
        bind(new UserAccount(
                user.id(),
                user.tenantId(),
                user.username(),
                user.password(),
                user.enabled(),
                user.roles(),
                user.permissions(),
                user.customDeptIds(),
                user.dataScopeType(),
                user.sessionVersion()
        ));
    }

    public static void clear() {
        AuthContextHolder.clear();
    }
}
