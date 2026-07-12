package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.common.context.CurrentOperatorSupplier;
import org.springframework.stereotype.Component;

@Component
public class AuthCurrentOperatorSupplier implements CurrentOperatorSupplier {

    @Override
    public String currentOperator() {
        return SecuritySupport.currentOperator();
    }
}