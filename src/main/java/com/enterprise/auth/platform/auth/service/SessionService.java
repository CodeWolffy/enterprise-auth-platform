package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.auth.AuthCookieConstants;
import com.enterprise.auth.platform.auth.model.UserSession;
import com.enterprise.auth.platform.auth.store.SessionStore;
import com.enterprise.auth.platform.config.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SessionService {

    private final SessionStore sessionStore;
    private final SecurityProperties securityProperties;

    public SessionService(SessionStore sessionStore, SecurityProperties securityProperties) {
        this.sessionStore = sessionStore;
        this.securityProperties = securityProperties;
    }

    public UserSession createSession(
            Long userId,
            String username,
            String tenantId,
            String clientIp,
            String device
    ) {
        Instant now = Instant.now();
        UserSession session = new UserSession(
                UUID.randomUUID().toString(),
                userId,
                username,
                tenantId,
                clientIp,
                StringUtils.hasText(device) ? device : "unknown",
                now,
                now.plus(securityProperties.sessionTtl()),
                now,
                true
        );
        sessionStore.save(session);
        return session;
    }

    public Optional<UserSession> findSession(String sessionId) {
        return sessionStore.findBySessionId(sessionId);
    }

    public List<UserSession> listSessions(Long userId) {
        return sessionStore.findByUserId(userId);
    }

    public void touch(String sessionId) {
        sessionStore.touch(sessionId);
    }

    public void deactivate(String sessionId) {
        sessionStore.deactivate(sessionId);
    }

    public void writeSessionCookie(HttpServletRequest request, HttpServletResponse response, UserSession session) {
        writeCookie(response, request, AuthCookieConstants.SESSION_COOKIE, session.sessionId(),
                Math.max(1L, securityProperties.sessionTtl().toSeconds()));
    }

    public void clearSessionCookie(HttpServletRequest request, HttpServletResponse response) {
        writeCookie(response, request, AuthCookieConstants.SESSION_COOKIE, "", 0);
    }

    public String resolveSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookieConstants.SESSION_COOKIE.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void writeCookie(
            HttpServletResponse response,
            HttpServletRequest request,
            String name,
            String value,
            long maxAgeSeconds
    ) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(securityProperties.cookieSecure() || request.isSecure())
                .sameSite(resolveSameSite())
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String resolveSameSite() {
        String value = securityProperties.cookieSameSite();
        if (!StringUtils.hasText(value)) {
            return "Lax";
        }
        String normalized = value.trim();
        return switch (normalized) {
            case "Strict", "Lax", "None" -> normalized;
            default -> "Lax";
        };
    }
}
