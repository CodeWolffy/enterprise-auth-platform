package com.enterprise.auth.platform.common;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.security.domain.EffectiveSecurityPolicy;

public class PasswordValidator {

    public static void validate(String password) {
        validate(password, EffectiveSecurityPolicy.defaults());
    }

    public static void validate(String password, EffectiveSecurityPolicy policy) {
        EffectiveSecurityPolicy resolved = policy == null ? EffectiveSecurityPolicy.defaults() : policy;
        if (password == null || password.length() < resolved.passwordMinLength() || password.length() > resolved.passwordMaxLength()) {
            throw new BusinessException("PASSWORD_INVALID", "密码长度需在" + resolved.passwordMinLength() + "到" + resolved.passwordMaxLength() + "位之间");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException("PASSWORD_INVALID", "密码不能包含空白字符");
        }
        if (resolved.passwordRequireLetter() && password.chars().noneMatch(Character::isLetter)) {
            throw new BusinessException("PASSWORD_INVALID", "密码必须包含字母");
        }
        if (resolved.passwordRequireNumber() && password.chars().noneMatch(Character::isDigit)) {
            throw new BusinessException("PASSWORD_INVALID", "密码必须包含数字");
        }
        if (resolved.passwordRequireSpecial() && password.chars().noneMatch(PasswordValidator::isSpecialChar)) {
            throw new BusinessException("PASSWORD_INVALID", "密码必须包含特殊字符");
        }
    }

    private static boolean isSpecialChar(int value) {
        return !Character.isLetterOrDigit(value) && !Character.isWhitespace(value);
    }
}
