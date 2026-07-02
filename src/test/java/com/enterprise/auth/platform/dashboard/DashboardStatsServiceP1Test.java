package com.enterprise.auth.platform.dashboard;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bind;
import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.clear;
import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.dashboard.application.DashboardStatsService;
import com.enterprise.auth.platform.modules.file.infrastructure.entity.SysStorageFileEntity;
import com.enterprise.auth.platform.modules.file.infrastructure.mapper.SysStorageFileMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DashboardStatsServiceP1Test {

    private static final String TENANT_A = "p1-dashboard-tenant-a";
    private static final String TENANT_B = "p1-dashboard-tenant-b";
    private static final String ADMIN = "p1_dashboard_admin_ut";
    private static final String VISIBLE = "p1_dashboard_visible_ut";
    private static final String HIDDEN = "p1_dashboard_hidden_ut";
    private static final String OTHER_TENANT = "p1_dashboard_other_tenant_ut";

    @Autowired
    private DashboardStatsService dashboardStatsService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysStorageFileMapper sysStorageFileMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        clear();
        TenantContext.clear();
        cleanup();
    }

    @Test
    void tenantScopeShouldExcludeOtherTenantRecords() {
        Long adminId = ensureUser(TENANT_A, ADMIN, 10L);
        Long visibleId = ensureUser(TENANT_A, VISIBLE, 10L);
        Long otherTenantUserId = ensureUser(TENANT_B, OTHER_TENANT, 10L);
        ensureRole(TENANT_A, "p1_dashboard_role_a_ut");
        ensureRole(TENANT_B, "p1_dashboard_role_b_ut");
        ensureFile(TENANT_A, "p1-dashboard-a-1", adminId, 100L);
        ensureFile(TENANT_A, "p1-dashboard-a-2", visibleId, 200L);
        ensureFile(TENANT_B, "p1-dashboard-b-1", otherTenantUserId, 900L);
        ensureAudit(TENANT_A, ADMIN);
        ensureAudit(TENANT_A, VISIBLE);
        ensureAudit(TENANT_A, ADMIN, "LOGIN_SUCCESS");
        ensureAudit(TENANT_A, VISIBLE, "LOGIN_FAILED");
        ensureAudit(TENANT_A, ADMIN, "ACCOUNT_LOCKED");
        ensureAudit(TENANT_B, OTHER_TENANT);
        ensureAudit(TENANT_B, OTHER_TENANT, "LOGIN_SUCCESS");

        TenantContext.setTenantId(TENANT_A);
        bind(principal(adminId, TENANT_A, ADMIN, DataScopeType.ALL));

        var stats = dashboardStatsService.stats();

        assertThat(stats.scope()).isEqualTo("TENANT");
        assertThat(stats.tenantId()).isEqualTo(TENANT_A);
        assertThat(stats.userCount()).isEqualTo(2);
        assertThat(stats.roleCount()).isEqualTo(1);
        assertThat(stats.tenantCount()).isEqualTo(1);
        assertThat(stats.fileCount()).isEqualTo(2);
        assertThat(stats.storageBytes()).isEqualTo(300L);
        assertThat(stats.operationLogCount()).isEqualTo(5);
        assertThat(stats.recentOperationLogCount()).isEqualTo(5);
        assertThat(stats.todayLoginCount()).isEqualTo(1);
        assertThat(stats.todayOperationLogCount()).isEqualTo(5);
        assertThat(stats.todayLoginFailedCount()).isEqualTo(1);
        assertThat(stats.todayRiskEventCount()).isEqualTo(1);
        assertThat(stats.dailyTrend()).hasSize(7);
        assertThat(stats.dailyTrend().get(6).loginCount()).isEqualTo(1);
        assertThat(stats.dailyTrend().get(6).operationCount()).isEqualTo(5);
        assertThat(stats.dailyTrend().get(6).loginFailedCount()).isEqualTo(1);
        assertThat(stats.serviceHealth()).extracting("code").contains("backend", "database", "redis", "storage");
        assertThat(stats.recentAuditEvents()).hasSize(5);
        assertThat(stats.recentAuditEvents()).allMatch(event -> TENANT_A.equals(event.tenantId()));
    }

    @Test
    void visibleScopeShouldRestrictUsersFilesAndOperationLogs() {
        Long adminId = ensureUser(TENANT_A, ADMIN, 20L);
        Long visibleId = ensureUser(TENANT_A, VISIBLE, 20L);
        Long hiddenId = ensureUser(TENANT_A, HIDDEN, 30L);
        ensureRole(TENANT_A, "p1_dashboard_role_visible_ut");
        ensureFile(TENANT_A, "p1-dashboard-visible-admin", adminId, 100L);
        ensureFile(TENANT_A, "p1-dashboard-visible-user", visibleId, 200L);
        ensureFile(TENANT_A, "p1-dashboard-hidden-user", hiddenId, 900L);
        ensureAudit(TENANT_A, ADMIN);
        ensureAudit(TENANT_A, VISIBLE);
        ensureAudit(TENANT_A, ADMIN, "LOGIN_SUCCESS");
        ensureAudit(TENANT_A, VISIBLE, "LOGIN_FAILED");
        ensureAudit(TENANT_A, HIDDEN);
        ensureAudit(TENANT_A, HIDDEN, "LOGIN_SUCCESS");
        ensureAudit(TENANT_A, HIDDEN, "LOGIN_FAILED");

        TenantContext.setTenantId(TENANT_A);
        bind(principal(adminId, TENANT_A, ADMIN, DataScopeType.DEPT));

        var stats = dashboardStatsService.stats();

        assertThat(stats.scope()).isEqualTo("VISIBLE");
        assertThat(stats.tenantId()).isEqualTo(TENANT_A);
        assertThat(stats.userCount()).isEqualTo(2);
        assertThat(stats.roleCount()).isEqualTo(1);
        assertThat(stats.tenantCount()).isEqualTo(1);
        assertThat(stats.fileCount()).isEqualTo(2);
        assertThat(stats.storageBytes()).isEqualTo(300L);
        assertThat(stats.operationLogCount()).isEqualTo(4);
        assertThat(stats.recentOperationLogCount()).isEqualTo(4);
        assertThat(stats.todayLoginCount()).isEqualTo(1);
        assertThat(stats.todayOperationLogCount()).isEqualTo(4);
        assertThat(stats.todayLoginFailedCount()).isEqualTo(1);
        assertThat(stats.todayRiskEventCount()).isZero();
        assertThat(stats.dailyTrend()).hasSize(7);
        assertThat(stats.dailyTrend().get(6).loginCount()).isEqualTo(1);
        assertThat(stats.dailyTrend().get(6).operationCount()).isEqualTo(4);
        assertThat(stats.dailyTrend().get(6).loginFailedCount()).isEqualTo(1);
        assertThat(stats.recentAuditEvents()).hasSize(4);
        assertThat(stats.recentAuditEvents()).extracting("operator").doesNotContain(HIDDEN);
    }

    private Long ensureUser(String tenantId, String username, Long deptId) {
        TenantContext.setTenantId(tenantId);
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId(tenantId);
        entity.setDeptId(deptId);
        entity.setUsername(username);
        entity.setDisplayName(username);
        entity.setPasswordHash("hash");
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
        return entity.getId();
    }

    private void ensureRole(String tenantId, String roleCode) {
        TenantContext.setTenantId(tenantId);
        SysRoleEntity entity = new SysRoleEntity();
        entity.setTenantId(tenantId);
        entity.setRoleCode(roleCode);
        entity.setRoleName(roleCode);
        entity.setDataScopeType("ALL");
        sysRoleMapper.insert(entity);
    }

    private void ensureFile(String tenantId, String fileKey, Long ownerUserId, Long size) {
        TenantContext.setTenantId(tenantId);
        SysStorageFileEntity entity = new SysStorageFileEntity();
        entity.setTenantId(tenantId);
        entity.setFileKey(fileKey);
        entity.setOriginalName(fileKey + ".txt");
        entity.setContentType("text/plain");
        entity.setFileSize(size);
        entity.setStorageType("LOCAL");
        entity.setBucketName("local");
        entity.setObjectKey(fileKey);
        entity.setVisibility("OWNER");
        entity.setOwnerUserId(ownerUserId);
        sysStorageFileMapper.insert(entity);
    }

    private void ensureAudit(String tenantId, String operator) {
        ensureAudit(tenantId, operator, "P1_DASHBOARD_SCOPE_TEST");
    }

    private void ensureAudit(String tenantId, String operator, String eventType) {
        jdbcTemplate.update(
                "INSERT INTO sys_log(tenant_id, event_type, operator, payload_json, created_at, request_id, client_ip) VALUES(?,?,?,?,?,?,?)",
                tenantId,
                eventType,
                operator,
                "{}",
                TimeSupport.utcNowDateTime(),
                "p1db-" + operator + "-" + eventType,
                "127.0.0.1"
        );
        String loginStatus = switch (eventType) {
            case "LOGIN_SUCCESS" -> "SUCCESS";
            case "LOGIN_FAILED" -> "FAILED";
            case "ACCOUNT_LOCKED" -> "LOCKED";
            default -> null;
        };
        if (loginStatus != null) {
            jdbcTemplate.update(
                    "INSERT INTO sys_login_log(tenant_id, user_name, status, msg, ip_addr, created_at) VALUES(?,?,?,?,?,?)",
                    tenantId,
                    operator,
                    loginStatus,
                    eventType,
                    "127.0.0.1",
                    TimeSupport.utcNowDateTime()
            );
        }
    }

    private UserAccount principal(Long userId, String tenantId, String username, DataScopeType dataScopeType) {
        return new UserAccount(
                userId,
                tenantId,
                username,
                "hash",
                true,
                Set.of(),
                Set.of("upms:dashboard:get"),
                Set.of(),
                dataScopeType,
                1
        );
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM sys_log WHERE tenant_id IN (?, ?) AND request_id LIKE 'p1db-%'",
                TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM sys_login_log WHERE tenant_id IN (?, ?) AND user_name IN (?, ?, ?, ?)",
                TENANT_A, TENANT_B, ADMIN, VISIBLE, HIDDEN, OTHER_TENANT);
        jdbcTemplate.update("DELETE FROM sys_storage_file WHERE tenant_id IN (?, ?) AND file_key LIKE 'p1-dashboard-%'",
                TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id IN (?, ?) AND role_code LIKE 'p1_dashboard_role_%'",
                TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id IN (?, ?) AND username IN (?, ?, ?, ?)",
                TENANT_A, TENANT_B, ADMIN, VISIBLE, HIDDEN, OTHER_TENANT);
    }
}