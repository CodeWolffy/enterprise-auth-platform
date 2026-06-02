package com.enterprise.auth.platform.modules.security.domain;

public record EffectiveSecurityPolicy(
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

    public static EffectiveSecurityPolicy defaults() {
        return new EffectiveSecurityPolicy(
                8,
                64,
                true,
                true,
                false,
                0,
                90,
                5,
                15,
                15,
                true
        );
    }
}