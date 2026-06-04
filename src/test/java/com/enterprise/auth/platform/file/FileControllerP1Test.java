package com.enterprise.auth.platform.file;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import java.nio.charset.StandardCharsets;
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
    private static final String TENANT_ID = "tenant-a";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", TENANT_ID, USERNAME);
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId(TENANT_ID);
        entity.setUsername(USERNAME);
        entity.setDisplayName(USERNAME);
        entity.setPasswordHash(passwordHasher.hash("FileTest@123"));
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
        userId = entity.getId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_storage_file WHERE tenant_id = ? AND owner_user_id = ?", TENANT_ID, userId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", TENANT_ID, USERNAME);
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