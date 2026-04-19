package com.enterprise.auth.platform.security;

import com.enterprise.auth.platform.auth.model.SessionPrincipal;
import com.enterprise.auth.platform.auth.model.UserSession;
import com.enterprise.auth.platform.auth.service.SessionService;
import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.tenant.TenantProperties;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String TENANT_ID_PARAM = "tenantId";

    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final TenantProperties tenantProperties;
    private final PlatformAdminSupport platformAdminSupport;
    private final ObjectMapper objectMapper;

    public SessionAuthenticationFilter(
            SessionService sessionService,
            UserRepository userRepository,
            TenantProperties tenantProperties,
            PlatformAdminSupport platformAdminSupport,
            ObjectMapper objectMapper
    ) {
        this.sessionService = sessionService;
        this.userRepository = userRepository;
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
        if (isBypassEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String sessionId = sessionService.resolveSessionId(request);
        if (!StringUtils.hasText(sessionId)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UserSession session = sessionService.findSession(sessionId)
                    .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "会话不存在"));
            if (!session.active() || Instant.now().isAfter(session.expiresAt())) {
                sessionService.deactivate(sessionId);
                throw new BusinessException("SESSION_EXPIRED", "会话已过期");
            }

            UserAccount user = userRepository.findById(session.userId())
                    .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "用户不存在"));
            if (!user.enabled()) {
                sessionService.deactivate(sessionId);
                throw new BusinessException("USER_DISABLED", "用户已禁用");
            }
            if (!user.tenantId().equals(session.tenantId())) {
                sessionService.deactivate(sessionId);
                throw new BusinessException("TENANT_MISMATCH", "租户上下文不匹配");
            }

            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(user, session.sessionId(), user.getAuthorities());
            String requestedTenantId = resolveRequestedTenant(request);
            String effectiveTenantId = platformAdminSupport.resolveEffectiveTenant(user, requestedTenantId);
            authentication.setDetails(new SessionPrincipal(session.sessionId(), effectiveTenantId, user.tenantId()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            TenantContext.setTenantId(effectiveTenantId);
            sessionService.touch(sessionId);
            filterChain.doFilter(request, response);
        } catch (BusinessException ex) {
            SecurityContextHolder.clearContext();
            writeAuthFailure(response, ex);
            return;
        }
    }

    private boolean isBypassEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "/api/auth/captcha".equals(uri)
            || "/api/auth/captcha/verify".equals(uri)
            || "/api/auth/csrf".equals(uri)
            || "/api/auth/login".equals(uri)
            || "/api/auth/register".equals(uri)
            || "/api/auth/register/options".equals(uri);
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

    private String resolveRequestedTenant(HttpServletRequest request) {
        String tenantId = request.getHeader(tenantProperties.headerName());
        if (!StringUtils.hasText(tenantId)) {
            tenantId = request.getParameter(TENANT_ID_PARAM);
        }
        return tenantId;
    }
}
