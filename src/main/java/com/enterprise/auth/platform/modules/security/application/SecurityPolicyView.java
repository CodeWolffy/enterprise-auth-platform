package com.enterprise.auth.platform.modules.security.application;

import com.enterprise.auth.platform.common.security.EffectiveSecurityPolicy;

public record SecurityPolicyView(
        int passwordMinLength,
        int passwordMaxLength,
        boolean passwordRequireLetter,
        boolean passwordRequireNumber,
        boolean passwordRequireSpecial,
        int passwordHistoryCount,
        int passwordExpireDays,
        int loginFailureMaxAttempts,
        int loginFailureLockMinutes,
        int loginFailureWindowMinutes,
        boolean captchaEnabled
) {

    public static SecurityPolicyView from(EffectiveSecurityPolicy policy) {
        return new SecurityPolicyView(
                policy.passwordMinLength(),
                policy.passwordMaxLength(),
                policy.passwordRequireLetter(),
                policy.passwordRequireNumber(),
                policy.passwordRequireSpecial(),
                policy.passwordHistoryCount(),
                policy.passwordExpireDays(),
                policy.loginFailureMaxAttempts(),
                policy.loginFailureLockMinutes(),
                policy.loginFailureWindowMinutes(),
                policy.captchaEnabled()
        );
    }
}