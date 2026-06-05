package com.enterprise.auth.platform.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.codegen.application.CodegenApplicationService;
import com.enterprise.auth.platform.modules.codegen.application.CodegenCommand;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "platform.codegen.output-root=target/codegen-ut")
class CodegenApplicationServiceP2Test {

    private static final String TABLE_NAME = "p2_codegen_order_ut";
    private static final Path OUTPUT_ROOT = Path.of("target/codegen-ut");

    @Autowired
    private CodegenApplicationService codegenApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws IOException {
        TenantContext.setTenantId("platform");
        cleanupOutput();
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
        jdbcTemplate.execute("""
                CREATE TABLE p2_codegen_order_ut (
                  id bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                  tenant_id varchar(64) NOT NULL COMMENT '租户',
                  order_no varchar(64) NOT NULL COMMENT '订单号',
                  amount decimal(12,2) NOT NULL COMMENT '金额',
                  enabled tinyint NOT NULL DEFAULT 1 COMMENT '启用状态',
                  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='P2 代码生成订单测试'
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_codegen_allowlist (tenant_id, table_name, description, enabled, created_by, updated_by, deleted)
                VALUES ('platform', ?, '代码生成单测白名单', 1, 'test', 'test', 0)
                ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), deleted = VALUES(deleted), updated_by = VALUES(updated_by)
                """, TABLE_NAME);
    }

    @AfterEach
    void tearDown() throws IOException {
        jdbcTemplate.update("DELETE FROM sys_codegen_allowlist WHERE table_name = ?", TABLE_NAME);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
        cleanupOutput();
        TenantContext.clear();
    }

    @Test
    void shouldListTablesAndPreviewGeneratedFiles() {
        var tables = codegenApplicationService.tables("codegen_order", 1, 20);

        assertThat(tables.records())
                .extracting("tableName")
                .contains(TABLE_NAME);
        assertThat(tables.records())
                .extracting("tableName")
                .doesNotContain("sys_user", "sys_mail_channel", "sys_config");

        var detail = codegenApplicationService.table(TABLE_NAME);
        assertThat(detail.columns())
                .extracting("columnName")
                .contains("id", "order_no", "amount");

        var preview = codegenApplicationService.preview(new CodegenCommand(
                TABLE_NAME,
                "orderGen",
                "com.enterprise.auth.platform.generated",
                "OrderGen",
                true,
                true,
                false,
                List.of(),
                false
        ));

        assertThat(preview.files()).hasSize(8);
        assertThat(preview.generatedRoot()).isEqualTo("SERVER_MANAGED");
        assertThat(preview.toString()).doesNotContain(OUTPUT_ROOT.toAbsolutePath().normalize().toString());
        assertThat(preview.files())
                .extracting("path")
                .contains(
                        "backend/src/main/java/com/enterprise/auth/platform/generated/modules/orderGen/infrastructure/entity/OrderGenEntity.java",
                        "backend/src/main/java/com/enterprise/auth/platform/generated/modules/orderGen/interfaces/OrderGenCrudRequest.java",
                        "frontend/src/views/generated/OrderGenView.vue"
                );
        assertThat(fileContent(preview, "OrderGenEntity.java"))
                .contains("@TableName(\"" + TABLE_NAME + "\")")
                .contains("@TableId(value = \"id\", type = IdType.AUTO)")
                .contains("@TableField(value = \"created_at\", fill = FieldFill.INSERT)");
        assertThat(fileContent(preview, "OrderGenCrudRequest.java"))
                .contains("public record OrderGenCrudRequest")
                .contains("@NotBlank String orderNo")
                .contains("@NotNull java.math.BigDecimal amount")
                .doesNotContain("tenantId")
                .doesNotContain("createdAt");
        assertThat(fileContent(preview, "OrderGenApplicationService.java"))
                .contains("PageResult<OrderGenEntity>")
                .contains("public OrderGenEntity create(OrderGenCrudRequest request)")
                .contains("public OrderGenEntity update(Long id, OrderGenCrudRequest request)")
                .contains("public void delete(Long id)")
                .contains("query.eq(OrderGenEntity::getTenantId, currentTenantId())");
        assertThat(fileContent(preview, "OrderGenController.java"))
                .contains("@PostMapping")
                .contains("@PutMapping(\"/{id}\")")
                .contains("@DeleteMapping(\"/{id}\")")
                .contains("@SaCheckPermission(\"orderGen:write\")");
        assertThat(fileContent(preview, "frontend/src/api/modules/orderGen.ts"))
                .contains("createOrderGen")
                .contains("updateOrderGen")
                .contains("deleteOrderGen");
        assertThat(fileContent(preview, "frontend/src/views/generated/OrderGenView.vue"))
                .contains("openForm")
                .contains("submit")
                .contains("remove(row)");
    }

    @Test
    void shouldRejectTableOutsideCurrentTenantAllowlist() {
        TenantContext.setTenantId("tenant-a");

        var tables = codegenApplicationService.tables("codegen_order", 1, 20);

        assertThat(tables.records())
                .extracting("tableName")
                .doesNotContain(TABLE_NAME);
        assertThatThrownBy(() -> codegenApplicationService.table(TABLE_NAME))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据表未纳入代码生成白名单");
    }

    @Test
    void shouldGenerateIntoIsolatedDirectoryAndProtectExistingFiles() {
        var command = new CodegenCommand(
                TABLE_NAME,
                "orderGen",
                "com.enterprise.auth.platform.generated",
                "OrderGen",
                true,
                true,
                false,
                null,
                false
        );

        var generated = codegenApplicationService.generate(command);

        assertThat(generated.files()).hasSize(8);
        assertThat(generated.outputRoot()).isEqualTo("SERVER_MANAGED");
        assertThat(generated.toString()).doesNotContain(OUTPUT_ROOT.toAbsolutePath().normalize().toString());
        assertThat(OUTPUT_ROOT.resolve("backend/src/main/java/com/enterprise/auth/platform/generated/modules/orderGen/infrastructure/entity/OrderGenEntity.java"))
                .exists();
        assertThatThrownBy(() -> codegenApplicationService.generate(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生成文件已存在");
    }

    @Test
    void shouldRejectInvalidGenerationInput() {
        assertThatThrownBy(() -> codegenApplicationService.table("../sys_user"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("表名格式不合法");

        assertThatThrownBy(() -> codegenApplicationService.preview(new CodegenCommand(
                TABLE_NAME,
                "bad-name",
                "com.enterprise.auth.platform.generated",
                "OrderGen",
                true,
                false,
                false,
                List.of(),
                false
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("moduleName 格式不合法");
    }

    private String fileContent(com.enterprise.auth.platform.modules.codegen.application.CodegenPreviewResult preview, String fileName) {
        return preview.files().stream()
                .filter(file -> file.path().endsWith(fileName))
                .findFirst()
                .orElseThrow()
                .content();
    }

    private void cleanupOutput() throws IOException {
        if (!Files.exists(OUTPUT_ROOT)) {
            return;
        }
        try (var paths = Files.walk(OUTPUT_ROOT)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    });
        }
    }
}