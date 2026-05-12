package com.enterprise.auth.platform.audit;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bind;
import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.clear;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.auth.platform.dto.req.AuditQuery;
import com.enterprise.auth.platform.service.AuditService;
import com.enterprise.auth.platform.dto.model.DataScopeType;
import com.enterprise.auth.platform.dao.entity.SysUserEntity;
import com.enterprise.auth.platform.dao.mapper.SysUserMapper;
import com.enterprise.auth.platform.common.TenantContext;
import com.enterprise.auth.platform.dto.model.UserAccount;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.enterprise.auth.platform.security.PasswordHasher;

@SpringBootTest
class AuditServiceQueryTest {

    private static final String AUDIT_SCOPE_USER = "audit_scope_user_ut";
    private static final String AUDIT_VISIBLE_USER = "audit_visible_user_ut";
    private static final String AUDIT_HIDDEN_USER = "audit_hidden_user_ut";

    @Autowired
    private AuditService auditService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHasher passwordHasher;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        clear();
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username in (?, ?, ?)",
                "tenant-a", AUDIT_SCOPE_USER, AUDIT_VISIBLE_USER, AUDIT_HIDDEN_USER);
    }

    @Test
    void shouldFilterByOperatorEventTypeAndTimeRange() throws InterruptedException {
        auditService.record("USER_CREATED", "alice", "platform", Map.of("bizId", "u-1"));
        Thread.sleep(1100L);
        Long fromEpochMs = System.currentTimeMillis();
        Thread.sleep(1100L);
        auditService.record("USER_UPDATED", "alice", "platform", Map.of("bizId", "u-2"));
        auditService.record("USER_UPDATED", "bob", "platform", Map.of("bizId", "u-3"));

        var page = auditService.query(new AuditQuery(
                "platform",
                "USER_UPDATED",
                "alice",
                null,
                null,
                fromEpochMs,
                null,
                1,
                20
        ));

        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).type()).isEqualTo("USER_UPDATED");
        assertThat(page.records().get(0).operator()).isEqualTo("alice");
    }

    @Test
    void shouldApplyDataScopeToAuditQuery() {
        TenantContext.setTenantId("tenant-a");
        Long scopedUserId = ensureUser(AUDIT_SCOPE_USER, 2L);
        ensureUser(AUDIT_VISIBLE_USER, 2L);
        ensureUser(AUDIT_HIDDEN_USER, 3L);

        UserAccount principal = new UserAccount(
                scopedUserId,
                "tenant-a",
                AUDIT_SCOPE_USER,
                passwordHasher.hash("AuditTest@123"),
                true,
                Set.of(),
                Set.of("audit:read"),
                Set.of(),
                DataScopeType.DEPT,
                1
        );
        bind(principal);

        auditService.record("AUDIT_SCOPE_TEST", AUDIT_VISIBLE_USER, "tenant-a", Map.of("bizId", "visible"));
        auditService.record("AUDIT_SCOPE_TEST", AUDIT_HIDDEN_USER, "tenant-a", Map.of("bizId", "hidden"));

        var page = auditService.query(new AuditQuery(
                "tenant-a",
                "AUDIT_SCOPE_TEST",
                null,
                null,
                null,
                null,
                null,
                1,
                20
        ));

        assertThat(page.records()).extracting(item -> item.details().get("bizId"))
                .contains("visible")
                .doesNotContain("hidden");
    }

    private Long ensureUser(String username, Long deptId) {
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", "tenant-a", username);
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId("tenant-a");
        entity.setDeptId(deptId);
        entity.setUsername(username);
        entity.setDisplayName(username);
        entity.setPasswordHash(passwordHasher.hash("AuditTest@123"));
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
        return entity.getId();
    }
}
