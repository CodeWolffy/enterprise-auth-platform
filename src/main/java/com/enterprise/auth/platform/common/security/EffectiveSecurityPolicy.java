package com.enterprise.auth.platform.common.security;

/**
 * 密码/登录策略纯数据契约，可被 common 与 security 模块共用，无业务模块依赖。
 */
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