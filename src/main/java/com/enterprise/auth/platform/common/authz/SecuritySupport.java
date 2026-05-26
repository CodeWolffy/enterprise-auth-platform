package com.enterprise.auth.platform.common.authz;

import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.common.context.AuthContextHolder;

public final class SecuritySupport {

    private SecuritySupport() {
    }

    public static String currentOperator() {
        return AuthContextHolder.currentUser()
                .map(user -> user.username())
                .orElseGet(SecuritySupport::saTokenOperatorOrSystem);
    }

    private static String saTokenOperatorOrSystem() {
        try {
            return StpUtil.isLogin() ? String.valueOf(StpUtil.getLoginId()) : "system";
        } catch (SaTokenContextException ignored) {
            return "system";
        }
    }
}