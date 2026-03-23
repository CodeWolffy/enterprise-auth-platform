package com.enterprise.auth.platform.security;

import com.enterprise.auth.platform.auth.AuthCookieConstants;
import com.enterprise.auth.platform.auth.model.TokenClaims;
import com.enterprise.auth.platform.auth.model.UserSession;
import com.enterprise.auth.platform.auth.service.AuthorizationSessionService;
import com.enterprise.auth.platform.auth.service.JwtService;
import com.enterprise.auth.platform.auth.store.SessionStore;
import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.tenant.TenantProperties;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String TENANT_ID_PARAM = "tenantId";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SessionStore sessionStore;
    private final JwtDecoder authorizationServerJwtDecoder;
    private final UserAccountJwtConverter userAccountJwtConverter;
    private final AuthorizationSessionService authorizationSessionService;
    private final TenantProperties tenantProperties;
    private final PlatformAdminSupport platformAdminSupport;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            SessionStore sessionStore,
            @Nullable JwtDecoder authorizationServerJwtDecoder,
            UserAccountJwtConverter userAccountJwtConverter,
            AuthorizationSessionService authorizationSessionService,
            TenantProperties tenantProperties,
            PlatformAdminSupport platformAdminSupport,
            ObjectMapper objectMapper
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.sessionStore = sessionStore;
        this.authorizationServerJwtDecoder = authorizationServerJwtDecoder;
        this.userAccountJwtConverter = userAccountJwtConverter;
        this.authorizationSessionService = authorizationSessionService;
        this.tenantProperties = tenantProperties;
        this.platformAdminSupport = platformAdminSupport;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveBearerToken(request);
        if (!StringUtils.hasText(token)) {
            log.warn("[AUTH-DIAG] No token found for {} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }
        log.warn("[AUTH-DIAG] Token found for {} {}, length={}", request.getMethod(), request.getRequestURI(), token.length());

        try {
            Optional<UsernamePasswordAuthenticationToken> authentication = authenticateCustomToken(request, token);
            if (authentication.isEmpty()) {
                log.warn("[AUTH-DIAG] Custom token auth returned empty for {} {}", request.getMethod(), request.getRequestURI());
                authentication = authenticateAuthorizationServerToken(request, token);
                if (authentication.isEmpty()) {
                    log.warn("[AUTH-DIAG] AuthServer token auth also returned empty for {} {}", request.getMethod(), request.getRequestURI());
                } else {
                    log.warn("[AUTH-DIAG] AuthServer token auth SUCCEEDED for {} {}", request.getMethod(), request.getRequestURI());
                }
            } else {
                log.warn("[AUTH-DIAG] Custom token auth SUCCEEDED for {} {}", request.getMethod(), request.getRequestURI());
            }
            if (authentication.isEmpty()) {
                if (!isTokenFailureBypassEndpoint(request)) {
                    writeAuthFailure(response, new BusinessException("INVALID_TOKEN", "Invalid access token"));
                    return;
                }
                filterChain.doFilter(request, response);
                return;
            }
            authentication.ifPresent(auth -> {
                SecurityContextHolder.getContext().setAuthentication(auth);
                if (auth.getPrincipal() instanceof UserAccount user) {
                    String requestedTenantId = resolveRequestedTenant(request);
                    TenantContext.setTenantId(platformAdminSupport.resolveEffectiveTenant(user, requestedTenantId));
                }
            });
        } catch (BusinessException ex) {
            log.warn("[AUTH-DIAG] Reject token for {} {}, code={}, message={}", request.getMethod(), request.getRequestURI(), ex.code(), ex.getMessage());
            SecurityContextHolder.clearContext();
            if (!isTokenFailureBypassEndpoint(request)) {
                writeAuthFailure(response, ex);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isTokenFailureBypassEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "/api/auth/captcha".equals(uri)
                || "/api/auth/csrf".equals(uri)
                || "/api/auth/login".equals(uri)
                || "/api/auth/refresh".equals(uri)
                || "/api/auth/oauth/exchange".equals(uri)
                || "/api/auth/oauth/refresh".equals(uri);
    }

    private void writeAuthFailure(HttpServletResponse response, BusinessException exception) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(exception.code(), exception.getMessage()));
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookieConstants.ACCESS_TOKEN_COOKIE.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Optional<UsernamePasswordAuthenticationToken> authenticateCustomToken(HttpServletRequest request, String token) {
        TokenClaims claims;
        try {
            claims = jwtService.decode(token);
        } catch (Exception ex) {
            return Optional.empty();
        }

        if (!"access".equals(claims.tokenType())) {
            throw new BusinessException("ACCESS_TOKEN_TYPE_INVALID", "Invalid access token type");
        }

        UserSession session = sessionStore.findBySessionId(claims.sessionId())
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "Session not found"));
        if (!session.active() || Instant.now().isAfter(session.expiresAt())) {
            throw new BusinessException("SESSION_EXPIRED", "Session expired");
        }

        UserAccount user = loadUserByTokenTenant(claims);

        validateSessionSubjectBinding(session, claims, user);
        validateTenantBinding(request, claims, user, session.tenantId());

        if (!user.enabled()) {
            authorizationSessionService.revoke(claims.sessionId());
            throw new BusinessException("USER_DISABLED", "User disabled");
        }
        if (user.sessionVersion() != claims.sessionVersion()) {
            throw new BusinessException("TOKEN_VERSION_MISMATCH", "Token version invalid");
        }

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        authentication.setDetails(claims);
        sessionStore.touch(claims.sessionId());
        return Optional.of(authentication);
    }

    private Optional<UsernamePasswordAuthenticationToken> authenticateAuthorizationServerToken(
            HttpServletRequest request,
            String token
    ) {
        if (authorizationServerJwtDecoder == null) {
            return Optional.empty();
        }

        try {
            Jwt jwt = authorizationServerJwtDecoder.decode(token);
            if (!userAccountJwtConverter.supports(jwt)) {
                log.debug("Authorization server jwt ignored: unsupported claims set");
                return Optional.empty();
            }

            TokenClaims claims = userAccountJwtConverter.toClaims(jwt);
            if (!"access".equals(claims.tokenType())) {
                throw new BusinessException("ACCESS_TOKEN_TYPE_INVALID", "Invalid access token type");
            }

            UserAccount user = loadUserByTokenTenant(claims);
            if (!user.enabled()) {
                authorizationSessionService.revoke(claims.sessionId());
                throw new BusinessException("USER_DISABLED", "User disabled");
            }
            if (user.sessionVersion() != claims.sessionVersion()) {
                authorizationSessionService.revoke(claims.sessionId());
                throw new BusinessException("TOKEN_VERSION_MISMATCH", "Token version invalid");
            }

            UserSession session = authorizationSessionService.findOrRestore(claims.sessionId(), user)
                    .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "Session not found"));
            validateSessionSubjectBinding(session, claims, user);
            validateTenantBinding(request, claims, user, session.tenantId());
            if (!session.active() || Instant.now().isAfter(session.expiresAt())) {
                authorizationSessionService.revoke(claims.sessionId());
                throw new BusinessException("SESSION_EXPIRED", "Session expired");
            }

            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(user, jwt.getTokenValue(), user.getAuthorities());
            authentication.setDetails(claims);
            authorizationSessionService.touch(claims.sessionId());
            return Optional.of(authentication);
        } catch (JwtException | IllegalArgumentException | ClassCastException ex) {
            log.warn("[AUTH-DIAG] AuthServer JWT decode failed: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }

    private void validateTenantBinding(
            HttpServletRequest request,
            TokenClaims claims,
            UserAccount user,
            String sessionTenantId
    ) {
        String tokenTenantId = claims.tenantId();
        if (!StringUtils.hasText(tokenTenantId)
                || !tokenTenantId.equals(user.tenantId())
                || !tokenTenantId.equals(sessionTenantId)) {
            authorizationSessionService.revoke(claims.sessionId());
            throw new BusinessException("TENANT_MISMATCH", "Tenant context mismatch");
        }

        String requestedTenantId = resolveRequestedTenant(request);
        if (!StringUtils.hasText(requestedTenantId)) {
            return;
        }
        if (requestedTenantId.equals(tokenTenantId)) {
            return;
        }
        if (!platformAdminSupport.canSwitchTenant(user, requestedTenantId)) {
            throw new BusinessException("TENANT_MISMATCH", "Tenant context mismatch");
        }
    }

    private void validateSessionSubjectBinding(UserSession session, TokenClaims claims, UserAccount user) {
        boolean mismatch = session.userId() == null
                || claims.userId() == null
                || user.id() == null
                || !session.userId().equals(claims.userId())
                || !session.userId().equals(user.id())
                || !StringUtils.hasText(session.username())
                || !session.username().equals(claims.username())
                || !session.username().equals(user.username());
        if (mismatch) {
            authorizationSessionService.revoke(claims.sessionId());
            throw new BusinessException("SESSION_SUBJECT_MISMATCH", "Session subject mismatch");
        }
    }

    private UserAccount loadUserByTokenTenant(TokenClaims claims) {
        return runInTokenTenant(claims.tenantId(), () -> userRepository.findById(claims.userId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found")));
    }

    private <T> T runInTokenTenant(String tenantId, Supplier<T> action) {
        if (!StringUtils.hasText(tenantId)) {
            return action.get();
        }
        String currentTenantId = TenantContext.getTenantId();
        boolean switched = !tenantId.equals(currentTenantId);
        if (!switched) {
            return action.get();
        }

        TenantContext.setTenantId(tenantId);
        try {
            return action.get();
        } finally {
            if (StringUtils.hasText(currentTenantId)) {
                TenantContext.setTenantId(currentTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private String resolveRequestedTenant(HttpServletRequest request) {
        String tenantId = request.getHeader(tenantProperties.headerName());
        if (!StringUtils.hasText(tenantId)) {
            tenantId = request.getParameter(TENANT_ID_PARAM);
        }
        return tenantId;
    }
}
