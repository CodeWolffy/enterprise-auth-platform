package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.common.PasswordValidator;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.dto.req.CreateUserRequest;
import com.enterprise.auth.platform.dto.req.RegisterRequest;
import com.enterprise.auth.platform.dto.resp.UserSummary;
import com.enterprise.auth.platform.service.RegisterAttemptService;
import com.enterprise.auth.platform.service.RegistrationPolicyService;
import com.enterprise.auth.platform.service.UserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RegistrationApplicationService {

    private final RegisterAttemptService registerAttemptService;
    private final RegistrationPolicyService registrationPolicyService;
    private final UserManagementService userManagementService;

    public RegistrationApplicationService(
            RegisterAttemptService registerAttemptService,
            RegistrationPolicyService registrationPolicyService,
            UserManagementService userManagementService
    ) {
        this.registerAttemptService = registerAttemptService;
        this.registrationPolicyService = registrationPolicyService;
        this.userManagementService = userManagementService;
    }

    public UserSummary register(RegisterRequest request, HttpServletRequest servletRequest) {
        String clientIp = clientIp(servletRequest);
        registerAttemptService.checkRateLimit(request.username(), clientIp);
        String defaultTenantId = registrationPolicyService.resolveDefaultTenantId();
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(defaultTenantId);
            Set<String> defaultRoleCodes = registrationPolicyService.resolveDefaultRoleCodes();

            PasswordValidator.validate(request.password());

            if (userManagementService.existsByUsername(request.username())) {
                throw new BusinessException("USERNAME_EXISTS", "用户名已存在");
            }

            CreateUserRequest createRequest = new CreateUserRequest(
                    request.username(),
                    request.displayName(),
                    request.mobile(),
                    request.email(),
                    request.password(),
                    null,
                    true,
                    defaultRoleCodes
            );
            return userManagementService.createUser(defaultTenantId, createRequest, "system");
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}