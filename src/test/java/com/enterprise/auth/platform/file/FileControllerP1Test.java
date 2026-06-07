package com.enterprise.auth.platform.file;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "platform.file.storage=local",
        "platform.file.local-root=target/test-files"
})
class FileControllerP1Test {

    private static final String USERNAME = "file_p1_user_ut";
    private static final String PLATFORM_ADMIN_USERNAME = "file_p1_platform_admin_ut";
    private static final String TENANT_ID = "tenant-a";
    private static final String OTHER_TENANT_ID = "tenant-b";
    private static final String PLATFORM_TENANT_ID = "platform";
    private static final String OTHER_FILE_KEY = "file_p1_other_tenant_ut";
    private static final String OTHER_OBJECT_KEY = "tenant/tenant-b/file_p1_other_tenant_ut.png";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long platformAdminUserId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", TENANT_ID, USERNAME);
        jdbcTemplate.update("DELETE ur FROM sys_user_role ur JOIN sys_user u ON ur.user_id = u.id WHERE ur.tenant_id = ? AND u.username = ?", PLATFORM_TENANT_ID, PLATFORM_ADMIN_USERNAME);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", PLATFORM_TENANT_ID, PLATFORM_ADMIN_USERNAME);
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId(TENANT_ID);
        entity.setUsername(USERNAME);
        entity.setDisplayName(USERNAME);
        entity.setPasswordHash(passwordHasher.hash("FileTest@123"));
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
        userId = entity.getId();
        platformAdminUserId = ensurePlatformAdminUser();
    }

    @AfterEach
    void tearDown() throws Exception {
        jdbcTemplate.update("DELETE FROM sys_storage_file WHERE tenant_id = ? AND owner_user_id = ?", TENANT_ID, userId);
        jdbcTemplate.update("DELETE FROM sys_storage_file WHERE file_key = ?", OTHER_FILE_KEY);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id = ? AND user_id = ?", PLATFORM_TENANT_ID, platformAdminUserId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", PLATFORM_TENANT_ID, PLATFORM_ADMIN_USERNAME);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", TENANT_ID, USERNAME);
        Files.deleteIfExists(Path.of("target/test-files", OTHER_OBJECT_KEY));
    }

    @Test
    void commonUploadShouldRejectPrivateVisibility() throws Exception {
        UserAccount principal = principal(Set.of("file:write"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                pngBytes()
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("visibility", "PRIVATE")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("通用文件上传不支持私有可见性，请通过具体业务授权链路创建私有文件"));
    }

    @Test
    void commonUploadShouldAcceptOwnerVisibility() throws Exception {
        UserAccount principal = principal(Set.of("file:write"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                pngBytes()
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("visibility", "OWNER")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("OWNER"))
                .andExpect(jsonPath("$.data.storageType").value("LOCAL"));
    }

    @Test
    void platformAdminScopedToNormalTenantShouldNotCrossTenantFiles() throws Exception {
        seedOtherTenantOwnerFile();
        UserAccount principal = platformAdminPrincipal();

        mockMvc.perform(get("/api/files")
                        .with(bearer(principal, TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.fileKey=='" + OTHER_FILE_KEY + "')]").doesNotExist());

        mockMvc.perform(get("/api/files/{fileKey}/metadata", OTHER_FILE_KEY)
                        .with(bearer(principal, TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/files/{fileKey}", OTHER_FILE_KEY)
                        .with(bearer(principal, TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(delete("/api/files/{fileKey}", OTHER_FILE_KEY)
                        .with(bearer(principal, TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM sys_storage_file WHERE file_key = ?",
                Integer.class,
                OTHER_FILE_KEY
        );
        assertThat(deleted).isZero();
    }

    @Test
    void platformAdminInPlatformTenantShouldManageAllTenantFiles() throws Exception {
        seedOtherTenantOwnerFile();
        UserAccount principal = platformAdminPrincipal();

        mockMvc.perform(get("/api/files")
                        .with(bearer(principal, PLATFORM_TENANT_ID))
                        .header("X-Tenant-Id", PLATFORM_TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.fileKey=='" + OTHER_FILE_KEY + "')]").exists());

        mockMvc.perform(get("/api/files/{fileKey}/metadata", OTHER_FILE_KEY)
                        .with(bearer(principal, PLATFORM_TENANT_ID))
                        .header("X-Tenant-Id", PLATFORM_TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileKey").value(OTHER_FILE_KEY))
                .andExpect(jsonPath("$.data.tenantId").value(OTHER_TENANT_ID));

        mockMvc.perform(get("/api/files/{fileKey}", OTHER_FILE_KEY)
                        .with(bearer(principal, PLATFORM_TENANT_ID))
                        .header("X-Tenant-Id", PLATFORM_TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pngBytes()));

        mockMvc.perform(delete("/api/files/{fileKey}", OTHER_FILE_KEY)
                        .with(bearer(principal, PLATFORM_TENANT_ID))
                        .header("X-Tenant-Id", PLATFORM_TENANT_ID))
                .andExpect(status().isOk());

        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM sys_storage_file WHERE file_key = ?",
                Integer.class,
                OTHER_FILE_KEY
        );
        assertThat(deleted).isOne();
    }

    private UserAccount principal(Set<String> permissions) {
        return new UserAccount(
                userId,
                TENANT_ID,
                USERNAME,
                passwordHasher.hash("FileTest@123"),
                true,
                Set.of(),
                permissions,
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }

    private UserAccount platformAdminPrincipal() {
        return new UserAccount(
                platformAdminUserId,
                PLATFORM_TENANT_ID,
                PLATFORM_ADMIN_USERNAME,
                passwordHasher.hash("FileTest@123"),
                true,
                Set.of("ADMIN"),
                Set.of(PermissionCodes.FILE_READ, PermissionCodes.FILE_WRITE, PermissionCodes.TENANT_READ),
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }

    private Long ensurePlatformAdminUser() {
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId(PLATFORM_TENANT_ID);
        entity.setUsername(PLATFORM_ADMIN_USERNAME);
        entity.setDisplayName(PLATFORM_ADMIN_USERNAME);
        entity.setPasswordHash(passwordHasher.hash("FileTest@123"));
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE tenant_id = ? AND role_code = 'ADMIN' AND deleted = 0",
                Long.class,
                PLATFORM_TENANT_ID
        );
        jdbcTemplate.update(
                "INSERT INTO sys_user_role(tenant_id, user_id, role_id, created_at, updated_at) VALUES(?,?,?,?,?)",
                PLATFORM_TENANT_ID,
                entity.getId(),
                roleId,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now()
        );
        return entity.getId();
    }

    private void seedOtherTenantOwnerFile() throws Exception {
        jdbcTemplate.update("DELETE FROM sys_storage_file WHERE file_key = ?", OTHER_FILE_KEY);
        Path target = Path.of("target/test-files", OTHER_OBJECT_KEY);
        Files.createDirectories(target.getParent());
        Files.write(target, pngBytes());
        jdbcTemplate.update(
                """
                INSERT INTO sys_storage_file(
                    tenant_id, file_key, original_name, content_type, file_size, storage_type,
                    bucket_name, object_key, visibility, owner_user_id, deleted, created_at, updated_at
                ) VALUES(?,?,?,?,?,?,?,?,?,?,0,NOW(),NOW())
                """,
                OTHER_TENANT_ID,
                OTHER_FILE_KEY,
                "other.png",
                MediaType.IMAGE_PNG_VALUE,
                pngBytes().length,
                "LOCAL",
                "local",
                OTHER_OBJECT_KEY,
                "OWNER",
                999_999L
        );
    }

    private byte[] pngBytes() {
        byte[] signature = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a
        };
        byte[] payload = "p1-file-test".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[signature.length + payload.length];
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        System.arraycopy(payload, 0, bytes, signature.length, payload.length);
        return bytes;
    }
}