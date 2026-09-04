package com.enterprise.auth.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.security.application.SecurityPolicyApplicationService;
import com.enterprise.auth.platform.modules.security.infrastructure.mapper.SysPlatformSecurityPolicyMapper;
import com.enterprise.auth.platform.modules.security.infrastructure.mapper.SysTenantSecurityPolicyMapper;
import org.junit.jupiter.api.Test;

class SecurityPolicyApplicationServiceTest {

    @Test
    void platformPolicyViewShouldRequirePlatformSuperAdmin() {
        SecurityPolicyApplicationService service = new SecurityPolicyApplicationService(
                mock(SysPlatformSecurityPolicyMapper.class),
                mock(SysTenantSecurityPolicyMapper.class),
                () -> false
        );

        assertThatThrownBy(service::platformPolicyView)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("ACCESS_DENIED");
                    assertThat(exception.getMessage()).isEqualTo("需要平台超级管理员权限");
                });
    }
}
