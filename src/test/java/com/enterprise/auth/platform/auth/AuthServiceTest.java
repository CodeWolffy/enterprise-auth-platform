package com.enterprise.auth.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.auth.dto.LoginRequest;
import com.enterprise.auth.platform.auth.dto.TokenResponse;
import com.enterprise.auth.platform.auth.service.AuthService;
import com.enterprise.auth.platform.auth.service.CaptchaService;
import com.enterprise.auth.platform.auth.service.JwtService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.SecurityProperties;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@SpringBootTest(properties = "app.security.expose-captcha-answer=true")
class AuthServiceTest {

    private static final String TEST_PASSWORD = "AuthTest@123";

    @Autowired
    private AuthService authService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SysUserMapper sysUserMapper;

    private String originalPasswordHash;
    private Integer originalEnabled;

    @BeforeEach
    void setUpAdminPassword() {
        SysUserEntity admin = loadAdminEntity();
        originalPasswordHash = admin.getPasswordHash();
        originalEnabled = admin.getEnabled();
        admin.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        admin.setEnabled(1);
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
        admin.setEnabled(originalEnabled == null ? 1 : originalEnabled);
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

    @Test
    void disabledUserShouldNotRefreshToken() {
        TokenResponse login = loginAsAdmin();
        SysUserEntity admin = loadAdminEntity();
        admin.setEnabled(0);
        admin.setUpdatedBy("test");
        sysUserMapper.updateById(admin);

        assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unknownUsernameShouldAlsoTriggerLockingPolicy() {
        for (int i = 0; i < 5; i++) {
            CaptchaService.CaptchaChallenge challenge = captchaService.create();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "127.0.0.1");
            assertThatThrownBy(() -> authService.login(
                    new LoginRequest(
                            "missing-user",
                            TEST_PASSWORD,
                            challenge.captchaId(),
                            challenge.previewCode(),
                            "platform",
                            "chrome"
                    ),
                    request
            )).isInstanceOf(BusinessException.class);
        }

        CaptchaService.CaptchaChallenge challenge = captchaService.create();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        assertThatThrownBy(() -> authService.login(
                new LoginRequest(
                        "missing-user",
                        TEST_PASSWORD,
                        challenge.captchaId(),
                        challenge.previewCode(),
                        "platform",
                        "chrome"
                ),
                request
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void usernameCaseVariantsShouldShareSameLockingCounter() {
        String[] usernameVariants = {"Case-Miss", "case-miss", "CASE-MISS", "CaSe-MiSs", "case-miss"};
        for (String username : usernameVariants) {
            CaptchaService.CaptchaChallenge challenge = captchaService.create();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "127.0.0.1");
            assertThatThrownBy(() -> authService.login(
                    new LoginRequest(
                            username,
                            TEST_PASSWORD,
                            challenge.captchaId(),
                            challenge.previewCode(),
                            "platform",
                            "chrome"
                    ),
                    request
            )).isInstanceOf(BusinessException.class);
        }

        CaptchaService.CaptchaChallenge challenge = captchaService.create();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        assertThatThrownBy(() -> authService.login(
                new LoginRequest(
                        "CASE-MISS",
                        TEST_PASSWORD,
                        challenge.captchaId(),
                        challenge.previewCode(),
                        "platform",
                        "chrome"
                ),
                request
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void refreshShouldRejectWhenTokenSubjectDoesNotMatchSession() {
        TokenResponse login = loginAsAdmin();
        var claims = jwtService.decode(login.refreshToken());
        String forgedToken = forgeRefreshToken(
                claims.sessionId(),
                claims.userId(),
                claims.tenantId(),
                claims.sessionVersion(),
                "tampered-admin"
        );

        assertThatThrownBy(() -> authService.refresh(forgedToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Session subject mismatch");
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

    private String forgeRefreshToken(String sessionId, Long userId, String tenantId, int sessionVersion, String subject) {
        SecretKeySpec key = new SecretKeySpec(
                securityProperties.jwtSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(10, ChronoUnit.MINUTES))
                .claim("uid", userId)
                .claim("sid", sessionId)
                .claim("tenant", tenantId)
                .claim("typ", "refresh")
                .claim("ver", sessionVersion)
                .build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }
}
