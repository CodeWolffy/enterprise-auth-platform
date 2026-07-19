package com.enterprise.auth.platform.infrastructure.security;

import com.enterprise.auth.platform.common.context.RequestContextCleaner;
import com.enterprise.auth.platform.modules.auth.domain.AuthContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationContextCleaner implements RequestContextCleaner {

    @Override
    public void clear() {
        AuthContextHolder.clear();
    }
}
