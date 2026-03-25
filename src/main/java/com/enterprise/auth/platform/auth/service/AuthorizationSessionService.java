package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.auth.dto.UserSessionResponse;
import com.enterprise.auth.platform.auth.model.UserSession;
import com.enterprise.auth.platform.auth.store.SessionStore;
import com.enterprise.auth.platform.common.time.TimeSupport;
import com.enterprise.auth.platform.common.web.RequestContext;
import com.enterprise.auth.platform.config.SecurityProperties;
import com.enterprise.auth.platform.user.model.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuthorizationSessionService {

    private final SessionStore sessionStore;
    private final OAuth2AuthorizationService authorizationService;
    private final SecurityProperties securityProperties;
    private final JdbcTemplate jdbcTemplate;
    private final JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper authorizationRowMapper;

    public AuthorizationSessionService(
            SessionStore sessionStore,
            OAuth2AuthorizationService authorizationService,
            SecurityProperties securityProperties,
            JdbcTemplate jdbcTemplate,
            @Qualifier("authorizationRowMapper")
            JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper authorizationRowMapper
    ) {
        this.sessionStore = sessionStore;
        this.authorizationService = authorizationService;
        this.securityProperties = securityProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationRowMapper = authorizationRowMapper;
    }

    public void activate(UserAccount user, String sessionId) {
        if (user == null || !StringUtils.hasText(sessionId)) {
            return;
        }
        Instant now = Instant.now();
        Optional<UserSession> existing = sessionStore.findBySessionId(sessionId);
        UserSession session = new UserSession(
                sessionId,
                user.id(),
                user.username(),
                user.tenantId(),
                existing.map(UserSession::clientIp).filter(StringUtils::hasText).orElse(RequestContext.getClientIp()),
                existing.map(UserSession::device).filter(StringUtils::hasText).orElse(resolveDevice()),
                existing.map(UserSession::issuedAt).orElse(now),
                resolveExpiresAt(sessionId, existing.map(UserSession::expiresAt).orElse(null)),
                now,
                true
        );
        sessionStore.save(session);
    }

    public Optional<UserSession> findOrRestore(String sessionId, UserAccount user) {
        if (!StringUtils.hasText(sessionId) || user == null) {
            return Optional.empty();
        }
        Optional<UserSession> existing = sessionStore.findBySessionId(sessionId);
        if (existing.isPresent()) {
            return existing;
        }
        OAuth2Authorization authorization = authorizationService.findById(sessionId);
        if (authorization == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        UserSession restored = new UserSession(
                sessionId,
                user.id(),
                user.username(),
                user.tenantId(),
                RequestContext.getClientIp(),
                resolveDevice(),
                resolveIssuedAt(authorization, now),
                resolveExpiresAt(authorization, now.plus(securityProperties.refreshTokenTtl())),
                now,
                true
        );
        sessionStore.save(restored);
        return Optional.of(restored);
    }

    public void touch(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        sessionStore.touch(sessionId);
    }

    public void revoke(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        OAuth2Authorization authorization = authorizationService.findById(sessionId);
        if (authorization != null) {
            authorizationService.remove(authorization);
        }
        sessionStore.deactivate(sessionId);
    }

    public List<UserSessionResponse> listSessions(UserAccount currentUser) {
        Map<String, UserSessionResponse> sessions = new LinkedHashMap<>();
        sessionStore.findByUserId(currentUser.id()).stream()
                .map(this::toResponse)
                .forEach(session -> sessions.put(session.sessionId(), session));

        loadAuthorizations(currentUser.username()).stream()
                .filter(this::supportsInteractiveSession)
                .map(authorization -> toAuthorizationSession(authorization, currentUser, sessions.get(authorization.getId())))
                .flatMap(Optional::stream)
                .forEach(session -> sessions.put(session.sessionId(), session));

        return sessions.values().stream()
                .sorted(Comparator.comparing(
                        UserSessionResponse::issuedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(200)
                .toList();
    }

    public Optional<SessionDescriptor> findSessionDescriptor(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        return sessionStore.findBySessionId(sessionId)
                .map(session -> new SessionDescriptor(
                        session.sessionId(),
                        session.userId(),
                        session.username(),
                        session.tenantId(),
                        session.active()
                ))
                .or(() -> Optional.ofNullable(authorizationService.findById(sessionId))
                        .flatMap(this::resolveAuthorizationDescriptor));
    }

    private Instant resolveExpiresAt(String sessionId, Instant existingExpiresAt) {
        OAuth2Authorization authorization = authorizationService.findById(sessionId);
        Instant fallback = Instant.now().plus(securityProperties.refreshTokenTtl());
        if (authorization == null) {
            return existingExpiresAt == null ? fallback : existingExpiresAt;
        }
        return resolveExpiresAt(authorization, existingExpiresAt == null ? fallback : existingExpiresAt);
    }

    private Instant resolveExpiresAt(OAuth2Authorization authorization, Instant fallback) {
        if (authorization.getRefreshToken() != null && authorization.getRefreshToken().getToken().getExpiresAt() != null) {
            return authorization.getRefreshToken().getToken().getExpiresAt();
        }
        if (authorization.getAccessToken() != null && authorization.getAccessToken().getToken().getExpiresAt() != null) {
            return authorization.getAccessToken().getToken().getExpiresAt();
        }
        return fallback;
    }

    private Instant resolveIssuedAt(OAuth2Authorization authorization, Instant fallback) {
        if (authorization.getAccessToken() != null && authorization.getAccessToken().getToken().getIssuedAt() != null) {
            return authorization.getAccessToken().getToken().getIssuedAt();
        }
        if (authorization.getRefreshToken() != null && authorization.getRefreshToken().getToken().getIssuedAt() != null) {
            return authorization.getRefreshToken().getToken().getIssuedAt();
        }
        return fallback;
    }

    private List<OAuth2Authorization> loadAuthorizations(String principalName) {
        if (!StringUtils.hasText(principalName)) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT authorization.*
                FROM oauth2_authorization authorization
                INNER JOIN sys_oauth_client client
                        ON client.id = CAST(authorization.registered_client_id AS UNSIGNED)
                       AND client.deleted = 0
                       AND client.client_status = 1
                WHERE authorization.principal_name = ?
                  AND authorization.authorization_grant_type <> 'client_credentials'
                ORDER BY COALESCE(
                        authorization.refresh_token_issued_at,
                        authorization.access_token_issued_at,
                        authorization.authorization_code_issued_at
                ) DESC
                LIMIT 200
                """, authorizationRowMapper, principalName);
    }

    private boolean supportsInteractiveSession(OAuth2Authorization authorization) {
        return authorization.getAccessToken() != null || authorization.getRefreshToken() != null;
    }

    private Optional<UserSessionResponse> toAuthorizationSession(
            OAuth2Authorization authorization,
            UserAccount currentUser,
            UserSessionResponse existing
    ) {
        Optional<SessionDescriptor> descriptor = resolveAuthorizationDescriptor(authorization);
        if (descriptor.isEmpty()) {
            return Optional.empty();
        }
        SessionDescriptor session = descriptor.get();
        if (!currentUser.id().equals(session.userId()) || !currentUser.tenantId().equals(session.tenantId())) {
            return Optional.empty();
        }
        Instant issuedAt = resolveIssuedAt(authorization, existing == null ? Instant.now() : TimeSupport.fromEpochMilli(existing.issuedAt()));
        Instant expiresAt = resolveExpiresAt(
                authorization,
                existing == null
                        ? issuedAt.plus(securityProperties.refreshTokenTtl())
                        : TimeSupport.fromEpochMilli(existing.expiresAt())
        );
        Instant lastAccessAt = existing == null ? issuedAt : TimeSupport.fromEpochMilli(existing.lastAccessAt());
        return Optional.of(new UserSessionResponse(
                authorization.getId(),
                session.username(),
                session.tenantId(),
                existing == null ? RequestContext.getClientIp() : existing.clientIp(),
                existing == null ? "unknown" : existing.device(),
                TimeSupport.toEpochMilli(issuedAt),
                TimeSupport.toEpochMilli(expiresAt),
                TimeSupport.toEpochMilli(lastAccessAt == null ? issuedAt : lastAccessAt),
                session.active()
        ));
    }

    private Optional<SessionDescriptor> resolveAuthorizationDescriptor(OAuth2Authorization authorization) {
        UserAccount user = resolveAuthorizationUser(authorization);
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(new SessionDescriptor(
                authorization.getId(),
                user.id(),
                user.username(),
                user.tenantId(),
                isAuthorizationActive(authorization)
        ));
    }

    private UserAccount resolveAuthorizationUser(OAuth2Authorization authorization) {
        Object authenticationAttribute = authorization.getAttribute(Authentication.class.getName());
        if (authenticationAttribute instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserAccount user) {
            return user;
        }
        Object principalAttribute = authorization.getAttribute(java.security.Principal.class.getName());
        if (principalAttribute instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserAccount user) {
            return user;
        }
        return null;
    }

    private boolean isAuthorizationActive(OAuth2Authorization authorization) {
        if (authorization.getRefreshToken() != null && authorization.getRefreshToken().isActive()) {
            return true;
        }
        return authorization.getAccessToken() != null && authorization.getAccessToken().isActive();
    }

    private UserSessionResponse toResponse(UserSession session) {
        return new UserSessionResponse(
                session.sessionId(),
                session.username(),
                session.tenantId(),
                session.clientIp(),
                session.device(),
                TimeSupport.toEpochMilli(session.issuedAt()),
                TimeSupport.toEpochMilli(session.expiresAt()),
                TimeSupport.toEpochMilli(session.lastAccessAt()),
                session.active()
        );
    }

    private String resolveDevice() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String userAgent = request.getHeader("User-Agent");
        return StringUtils.hasText(userAgent) ? userAgent : "unknown";
    }

    public record SessionDescriptor(
            String sessionId,
            Long userId,
            String username,
            String tenantId,
            boolean active
    ) {
    }
}
