package com.enterprise.auth.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.auth.dto.LoginRequest;
import com.enterprise.auth.platform.auth.dto.TokenResponse;
import com.enterprise.auth.platform.auth.service.AuthService;
import com.enterprise.auth.platform.auth.service.CaptchaService;
import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.permission.dto.CreatePermissionRequest;
import com.enterprise.auth.platform.permission.service.PermissionManagementService;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.system.dto.DictCrudRequest;
import com.enterprise.auth.platform.system.service.SystemManagementService;
import com.enterprise.auth.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "app.security.expose-captcha-answer=true")
@ActiveProfiles("mysql")
class MysqlPersistenceIntegrationTest {

    private static final String TEST_PASSWORD = "MysqlTest@123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("enterprise_auth_platform")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.persistence.database-enabled", () -> true);
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private PermissionManagementService permissionManagementService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private SystemManagementService systemManagementService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String originalAdminPasswordHash;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("platform");
        SysUserEntity admin = loadAdmin();
        originalAdminPasswordHash = admin.getPasswordHash();
        admin.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        admin.setUpdatedBy("test");
        sysUserMapper.updateById(admin);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (originalAdminPasswordHash != null) {
            SysUserEntity admin = loadAdmin();
            admin.setPasswordHash(originalAdminPasswordHash);
            admin.setUpdatedBy("test");
            sysUserMapper.updateById(admin);
        }
    }

    @Test
    void loginShouldWorkAgainstMysqlSeedData() {
        CaptchaService.CaptchaChallenge challenge = captchaService.create();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "127.0.0.1");

        TokenResponse tokenResponse = authService.login(
                new LoginRequest("admin", TEST_PASSWORD, challenge.captchaId(), challenge.previewCode(), "platform", "chrome"),
                request
        );

        SysUserEntity admin = loadAdmin();

        assertThat(tokenResponse.accessToken()).isNotBlank();
        assertThat(tokenResponse.refreshToken()).isNotBlank();
        assertThat(admin).isNotNull();
        assertThat(admin.getLastLoginAt()).isNotNull();
        assertThat(admin.getLastLoginIp()).isEqualTo("127.0.0.1");
    }

    @Test
    void permissionCrudShouldWorkAgainstMysql() {
        String permissionCode = "report:export:" + System.nanoTime();
        CatalogService.PermissionView created = permissionManagementService.create(
                new CreatePermissionRequest("report", "export", "tenant", "报表导出", permissionCode)
        );

        assertThat(created.permissionCode()).isEqualTo(permissionCode);
        assertThat(created.permissionName()).isEqualTo("报表导出");
        assertThat(catalogService.permissions()).extracting(CatalogService.PermissionView::permissionCode).contains(permissionCode);

        permissionManagementService.delete(created.id());

        assertThat(catalogService.permissions()).extracting(CatalogService.PermissionView::permissionCode).doesNotContain(permissionCode);
    }

    @Test
    void systemCrudShouldWorkAgainstMysql() {
        String dictCode = "INFO_" + System.nanoTime();
        SystemManagementService.DictView created = systemManagementService.createDict(
                new DictCrudRequest("notify_level", dictCode, "普通通知")
        );

        assertThat(systemManagementService.dicts(null, null, null, 1, 50, "createdAt", "asc").records())
                .extracting(SystemManagementService.DictView::dictCode)
                .contains(dictCode);

        systemManagementService.deleteDict(created.id());

        assertThat(systemManagementService.dicts(null, null, null, 1, 50, "createdAt", "asc").records())
                .extracting(SystemManagementService.DictView::dictCode)
                .doesNotContain(dictCode);
    }

    @Test
    void softDeleteShouldKeepRowsAndMarkDeletedFlag() {
        String permissionCode = "report:archive:" + System.nanoTime();
        CatalogService.PermissionView permission = permissionManagementService.create(
                new CreatePermissionRequest("report", "archive", "tenant", "报表归档", permissionCode)
        );
        permissionManagementService.delete(permission.id());

        Integer permissionDeleted = jdbcTemplate.queryForObject(
                "select deleted from sys_permission where id = ?",
                Integer.class,
                permission.id()
        );
        assertThat(permissionDeleted).isEqualTo(1);

        String dictCode = "TRACE_" + System.nanoTime();
        SystemManagementService.DictView dict = systemManagementService.createDict(
                new DictCrudRequest("audit_level", dictCode, "审计追踪")
        );
        systemManagementService.deleteDict(dict.id());

        Integer dictDeleted = jdbcTemplate.queryForObject(
                "select deleted from sys_dict where id = ?",
                Integer.class,
                dict.id()
        );
        assertThat(dictDeleted).isEqualTo(1);
    }

    private SysUserEntity loadAdmin() {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, "platform")
                .eq(SysUserEntity::getUsername, "admin")
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
    }
}
