package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.common.PasswordValidator;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.user.interfaces.CreateUserRequest;
import com.enterprise.auth.platform.modules.auth.interfaces.RegisterRequest;
import com.enterprise.auth.platform.modules.user.interfaces.UserSummary;
import com.enterprise.auth.platform.modules.auth.application.RegisterAttemptService;
import com.enterprise.auth.platform.modules.auth.application.RegistrationPolicyService;
import com.enterprise.auth.platform.modules.user.application.UserManagementService;
import com.enterprise.auth.platform.common.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RegistrationApplicationService {

    private final RegisterAttemptService registerAttemptService;
    private final RegistrationPolicyService registrationPolicyService;
    private final UserManagementService userManagementService;
    private final ClientIpResolver clientIpResolver;
    private final CaptchaService captchaService;

    public RegistrationApplicationService(
            RegisterAttemptService registerAttemptService,
            RegistrationPolicyService registrationPolicyService,
            UserManagementService userManagementService,
            ClientIpResolver clientIpResolver,
            CaptchaService captchaService
    ) {
        this.registerAttemptService = registerAttemptService;
        this.registrationPolicyService = registrationPolicyService;
        this.userManagementService = userManagementService;
        this.clientIpResolver = clientIpResolver;
        this.captchaService = captchaService;
    }

    public UserSummary register(RegisterRequest request, HttpServletRequest servletRequest) {
        captchaService.secondaryVerify(request.captchaId());
        String clientIp = clientIpResolver.resolve(servletRequest);
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
}