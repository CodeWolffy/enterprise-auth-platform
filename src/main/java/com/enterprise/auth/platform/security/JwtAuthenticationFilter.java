package com.enterprise.auth.platform.security;

import com.enterprise.auth.platform.auth.model.TokenClaims;
import com.enterprise.auth.platform.auth.model.UserSession;
import com.enterprise.auth.platform.auth.service.AuthorizationSessionService;
import com.enterprise.auth.platform.auth.service.JwtService;
import com.enterprise.auth.platform.auth.store.SessionStore;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.tenant.TenantProperties;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
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

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            SessionStore sessionStore,
            @Nullable JwtDecoder authorizationServerJwtDecoder,
            UserAccountJwtConverter userAccountJwtConverter,
            AuthorizationSessionService authorizationSessionService,
            TenantProperties tenantProperties
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.sessionStore = sessionStore;
        this.authorizationServerJwtDecoder = authorizationServerJwtDecoder;
        this.userAccountJwtConverter = userAccountJwtConverter;
        this.authorizationSessionService = authorizationSessionService;
        this.tenantProperties = tenantProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7);
        try {
            Optional<UsernamePasswordAuthenticationToken> authentication = authenticateCustomToken(request, token);
            if (authentication.isEmpty()) {
                authentication = authenticateAuthorizationServerToken(request, token);
            }
            authentication.ifPresent(auth -> {
                SecurityContextHolder.getContext().setAuthentication(auth);
                if (auth.getPrincipal() instanceof UserAccount user) {
                    // Bind tenant context to token tenant to avoid forged header/param switching.
                    TenantContext.setTenantId(user.tenantId());
                }
            });
        } catch (BusinessException ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
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

        UserAccount user = userRepository.findById(claims.userId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

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
                return Optional.empty();
            }

            TokenClaims claims = userAccountJwtConverter.toClaims(jwt);
            if (!"access".equals(claims.tokenType())) {
                throw new BusinessException("ACCESS_TOKEN_TYPE_INVALID", "Invalid access token type");
            }

            UserAccount user = userRepository.findById(claims.userId())
                    .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
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
            log.debug("Ignore invalid authorization server token: {}", ex.getMessage());
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
        if (StringUtils.hasText(requestedTenantId) && !requestedTenantId.equals(tokenTenantId)) {
            throw new BusinessException("TENANT_MISMATCH", "Tenant context mismatch");
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
