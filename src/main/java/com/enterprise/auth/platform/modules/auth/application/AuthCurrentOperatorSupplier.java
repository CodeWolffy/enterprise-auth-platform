package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.common.context.CurrentOperatorSupplier;
import com.enterprise.auth.platform.modules.auth.domain.AuthContextHolder;
import java.util.Optional;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class AuthCurrentOperatorSupplier implements CurrentOperatorSupplier {

    @Override
    public String currentOperator() {
        return SecuritySupport.currentOperator();
    }

    @Override
    public Optional<String> operatorTenantId() {
        return AuthContextHolder.currentSession()
                .map(session -> session.operatorTenantId())
                .filter(StringUtils::hasText);
    }
}
