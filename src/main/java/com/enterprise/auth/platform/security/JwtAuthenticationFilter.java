package com.enterprise.auth.platform.security;

import com.enterprise.auth.platform.auth.model.TokenClaims;
import com.enterprise.auth.platform.auth.model.UserSession;
import com.enterprise.auth.platform.auth.service.JwtService;
import com.enterprise.auth.platform.auth.store.SessionStore;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SessionStore sessionStore;
    private final JwtDecoder authorizationServerJwtDecoder;
    private final UserAccountJwtConverter userAccountJwtConverter;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            SessionStore sessionStore,
            @Nullable JwtDecoder authorizationServerJwtDecoder,
            UserAccountJwtConverter userAccountJwtConverter
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.sessionStore = sessionStore;
        this.authorizationServerJwtDecoder = authorizationServerJwtDecoder;
        this.userAccountJwtConverter = userAccountJwtConverter;
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
            Optional<UsernamePasswordAuthenticationToken> authentication = authenticateCustomToken(token);
            if (authentication.isEmpty()) {
                authentication = authenticateAuthorizationServerToken(token);
            }
            authentication.ifPresent(auth -> SecurityContextHolder.getContext().setAuthentication(auth));
        } catch (BusinessException ex) {
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    private Optional<UsernamePasswordAuthenticationToken> authenticateCustomToken(String token) {
        TokenClaims claims;
        try {
            claims = jwtService.decode(token);
        } catch (Exception ex) {
            return Optional.empty();
        }
        if (!"access".equals(claims.tokenType())) {
            throw new BusinessException("访问令牌类型错误");
        }
        UserSession session = sessionStore.findBySessionId(claims.sessionId())
                .orElseThrow(() -> new BusinessException("会话不存在"));
        if (!session.active() || Instant.now().isAfter(session.expiresAt())) {
            throw new BusinessException("会话已过期");
        }
        UserAccount user = userRepository.findById(claims.userId())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (user.sessionVersion() != claims.sessionVersion()) {
            throw new BusinessException("令牌版本已失效");
        }

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        authentication.setDetails(claims);
        sessionStore.touch(claims.sessionId());
        return Optional.of(authentication);
    }

    private Optional<UsernamePasswordAuthenticationToken> authenticateAuthorizationServerToken(String token) {
        if (authorizationServerJwtDecoder == null) {
            return Optional.empty();
        }
        try {
            Jwt jwt = authorizationServerJwtDecoder.decode(token);
            if (!userAccountJwtConverter.supports(jwt)) {
                return Optional.empty();
            }
            return Optional.of(userAccountJwtConverter.convert(jwt));
        } catch (JwtException | IllegalArgumentException | ClassCastException ex) {
            log.debug("Ignore invalid authorization server token: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
