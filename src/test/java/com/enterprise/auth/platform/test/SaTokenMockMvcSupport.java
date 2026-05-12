package com.enterprise.auth.platform.test;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.dto.model.SessionPrincipal;
import com.enterprise.auth.platform.security.AuthContextHolder;
import com.enterprise.auth.platform.dto.model.UserAccount;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class SaTokenMockMvcSupport {

    private SaTokenMockMvcSupport() {
    }

    public static RequestPostProcessor bearer(UserAccount user) {
        return request -> {
            String token = StpUtil.createLoginSession(user.id(), new SaLoginModel().setDevice("mockmvc"));
            SaSession tokenSession = StpUtil.getTokenSessionByToken(token);
            tokenSession.set("username", user.username());
            tokenSession.set("userId", user.id());
            tokenSession.set("tenantId", user.tenantId());
            tokenSession.set("sessionVersion", user.sessionVersion());
            tokenSession.set("roles", user.roles());
            tokenSession.set("permissions", user.permissions());
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

    public static void clear() {
        AuthContextHolder.clear();
    }
}
