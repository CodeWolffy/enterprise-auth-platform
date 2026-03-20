package com.enterprise.auth.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.auth.dto.LoginRequest;
import com.enterprise.auth.platform.auth.dto.TokenResponse;
import com.enterprise.auth.platform.auth.service.AuthService;
import com.enterprise.auth.platform.auth.service.CaptchaService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class AuthServiceTest {

    private static final String TEST_PASSWORD = "AuthTest@123";

    @Autowired
    private AuthService authService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SysUserMapper sysUserMapper;

    private String originalPasswordHash;

    @BeforeEach
    void setUpAdminPassword() {
        SysUserEntity admin = loadAdminEntity();
        originalPasswordHash = admin.getPasswordHash();
        admin.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        admin.setUpdatedBy("test");
        sysUserMapper.updateById(admin);
    }

    @AfterEach
    void restoreAdminPassword() {
        if (originalPasswordHash == null) {
            return;
        }
        SysUserEntity admin = loadAdminEntity();
        admin.setPasswordHash(originalPasswordHash);
        admin.setUpdatedBy("test");
        sysUserMapper.updateById(admin);
    }

    @Test
    void loginRefreshAndSessionFlowWorks() {
        TokenResponse login = loginAsAdmin();

        assertThat(login.accessToken()).isNotBlank();
        assertThat(login.refreshToken()).isNotBlank();
        assertThat(login.sessionId()).isNotBlank();

        TokenResponse refreshed = authService.refresh(login.refreshToken());
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.sessionId()).isEqualTo(login.sessionId());
    }

    @Test
    void logoutShouldInvalidateRefreshToken() {
        TokenResponse login = loginAsAdmin();

        authService.logout(login.sessionId(), "admin", "platform");

        assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void forceOfflineShouldInvalidateRefreshToken() {
        TokenResponse login = loginAsAdmin();
        UserAccount currentUser = userRepository.findByUsername("platform", "admin").orElseThrow();

        authService.forceOffline(currentUser, login.sessionId());

        assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                .isInstanceOf(BusinessException.class);
    }

    private TokenResponse loginAsAdmin() {
        CaptchaService.CaptchaChallenge challenge = captchaService.create();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        return authService.login(
                new LoginRequest(
                        "admin",
                        TEST_PASSWORD,
                        challenge.captchaId(),
                        challenge.previewCode(),
                        "platform",
                        "chrome"
                ),
                request
        );
    }

    private SysUserEntity loadAdminEntity() {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, "platform")
                .eq(SysUserEntity::getUsername, "admin")
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
    }
}
