package com.enterprise.auth.platform.common;

import com.enterprise.auth.platform.common.convention.exception.BusinessException;
import java.util.regex.Pattern;

public class PasswordValidator {

    private static final Pattern PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)\\S{8,64}$");

    public static void validate(String password) {
        if (password == null || !PATTERN.matcher(password).matches()) {
            throw new BusinessException("PASSWORD_INVALID", "密码至少8位，包含字母和数字");
        }
    }
}
