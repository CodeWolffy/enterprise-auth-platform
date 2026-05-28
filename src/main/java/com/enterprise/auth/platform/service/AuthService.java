package com.enterprise.auth.platform.service;

import com.enterprise.auth.platform.dto.req.LoginRequest;
import com.enterprise.auth.platform.dto.resp.TokenSessionResponse;
import com.enterprise.auth.platform.dto.resp.PermissionSnapshotResponse;
import com.enterprise.auth.platform.dto.resp.UserSessionResponse;
import com.enterprise.auth.platform.dto.model.PageResult;
import com.enterprise.auth.platform.dto.req.RegisterRequest;
import com.enterprise.auth.platform.dto.model.UserAccount;
import com.enterprise.auth.platform.dto.resp.UserSummary;
import com.enterprise.auth.platform.modules.auth.application.LoginApplicationService;
import com.enterprise.auth.platform.modules.auth.application.RegistrationApplicationService;
import com.enterprise.auth.platform.modules.auth.application.SessionApplicationService;
import com.enterprise.auth.platform.modules.auth.application.TenantSwitchApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final LoginApplicationService loginApplicationService;
    private final RegistrationApplicationService registrationApplicationService;
    private final SessionApplicationService sessionApplicationService;
    private final TenantSwitchApplicationService tenantSwitchApplicationService;

    public AuthService(
            LoginApplicationService loginApplicationService,
            RegistrationApplicationService registrationApplicationService,
            SessionApplicationService sessionApplicationService,
            TenantSwitchApplicationService tenantSwitchApplicationService
    ) {
        this.loginApplicationService = loginApplicationService;
        this.registrationApplicationService = registrationApplicationService;
        this.sessionApplicationService = sessionApplicationService;
        this.tenantSwitchApplicationService = tenantSwitchApplicationService;
    }

    public TokenSessionResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        return loginApplicationService.login(request, servletRequest);
    }

    public void logout(String sessionId, String username, String tenantId) {
        sessionApplicationService.logout(sessionId, username, tenantId);
    }

    public PermissionSnapshotResponse switchTenant(UserAccount currentUser, String targetTenantId) {
        return tenantSwitchApplicationService.switchTenant(currentUser, targetTenantId);
    }

    public List<UserSessionResponse> sessions(UserAccount currentUser, String scope, String currentToken) {
        return sessionApplicationService.sessions(currentUser, scope, currentToken);
    }

    public PageResult<UserSessionResponse> sessions(
            UserAccount currentUser,
            String scope,
            String currentToken,
            Integer page,
            Integer size
    ) {
        return sessionApplicationService.sessions(currentUser, scope, currentToken, page, size);
    }

    public void forceOffline(UserAccount currentUser, String sessionId) {
        sessionApplicationService.forceOffline(currentUser, sessionId);
    }

    public UserSummary register(RegisterRequest request, HttpServletRequest servletRequest) {
        return registrationApplicationService.register(request, servletRequest);
    }
}
