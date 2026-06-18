package com.enterprise.auth.platform.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.codegen.application.CodegenApplicationService;
import com.enterprise.auth.platform.modules.codegen.application.CodegenCommand;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataDtos;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataService;
import com.enterprise.auth.platform.modules.codegen.interfaces.CodegenController;
import com.enterprise.auth.platform.modules.codegen.interfaces.CodegenRequest;
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
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "platform.codegen.output-root=target/codegen-ut")
class CodegenApplicationServiceP2Test {

    private static final String TABLE_NAME = "p2_codegen_order_ut";
    private static final Path OUTPUT_ROOT = Path.of("target/codegen-ut");

    @Autowired
    private CodegenApplicationService codegenApplicationService;

    @Autowired
    private CodegenMetadataService codegenMetadataService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws IOException {
        TenantContext.setTenantId("platform");
        cleanupOutput();
        cleanupGeneratedMenus();
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
        jdbcTemplate.update("DELETE FROM codegen_table_column WHERE table_id IN (SELECT id FROM codegen_table WHERE table_name = ?)", TABLE_NAME);
        jdbcTemplate.update("DELETE FROM codegen_table WHERE table_name = ?", TABLE_NAME);
        jdbcTemplate.update("DELETE FROM sys_codegen_allowlist WHERE table_name = ?", TABLE_NAME);
        cleanupGeneratedMenus();
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

        assertThat(preview.files()).hasSize(10);
        assertThat(preview.generatedRoot()).isEqualTo("SERVER_MANAGED");
        assertThat(preview.toString()).doesNotContain(OUTPUT_ROOT.toAbsolutePath().normalize().toString());
        assertThat(preview.files())
                .extracting("path")
                .contains(
                        "backend/src/main/java/com/enterprise/auth/platform/generated/modules/orderGen/infrastructure/entity/OrderGenEntity.java",
                        "backend/src/main/java/com/enterprise/auth/platform/generated/modules/orderGen/interfaces/OrderGenCreateRequest.java",
                        "backend/src/main/java/com/enterprise/auth/platform/generated/modules/orderGen/interfaces/OrderGenUpdateRequest.java",
                        "backend/src/main/java/com/enterprise/auth/platform/generated/modules/orderGen/interfaces/OrderGenQueryRequest.java",
                        "frontend/src/views/generated/OrderGenView.vue"
                );
        assertThat(fileContent(preview, "OrderGenEntity.java"))
                .contains("@TableName(\"" + TABLE_NAME + "\")")
                .contains("@TableId(value = \"id\", type = IdType.AUTO)")
                .contains("@TableField(value = \"created_at\", fill = FieldFill.INSERT)");
        assertThat(fileContent(preview, "OrderGenCreateRequest.java"))
                .contains("public record OrderGenCreateRequest")
                .contains("@NotBlank String orderNo")
                .contains("@NotNull java.math.BigDecimal amount")
                .doesNotContain("tenantId")
                .doesNotContain("createdAt");
        assertThat(fileContent(preview, "OrderGenUpdateRequest.java"))
                .contains("public record OrderGenUpdateRequest")
                .contains("String orderNo")
                .contains("java.math.BigDecimal amount")
                .doesNotContain("tenantId")
                .doesNotContain("createdAt");
        assertThat(fileContent(preview, "OrderGenQueryRequest.java"))
                .contains("public record OrderGenQueryRequest")
                .contains("Integer page")
                .contains("Integer size")
                .contains("String orderNo")
                .doesNotContain("tenantId")
                .doesNotContain("createdAt");
        assertThat(fileContent(preview, "OrderGenApplicationService.java"))
                .contains("PageResult<OrderGenEntity>")
                .contains("public OrderGenEntity create(OrderGenCreateRequest request)")
                .contains("public OrderGenEntity update(Long id, OrderGenUpdateRequest request)")
                .contains("private LambdaQueryWrapper<OrderGenEntity> baseQuery(OrderGenQueryRequest request)")
                .contains("public void delete(Long id)")
                .contains("query.eq(OrderGenEntity::getTenantId, currentTenantId())");
        assertThat(fileContent(preview, "OrderGenController.java"))
                .contains("@PostMapping")
                .contains("@PutMapping(\"/{id}\")")
                .contains("@DeleteMapping(\"/{id}\")")
                .contains("@SaCheckPermission(\"orderGen:page\")")
                .contains("@SaCheckPermission(\"orderGen:get\")")
                .contains("@SaCheckPermission(\"orderGen:add\")")
                .contains("@SaCheckPermission(\"orderGen:edit\")")
                .contains("@SaCheckPermission(\"orderGen:del\")");
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

        assertThat(generated.files()).hasSize(10);
        assertThat(generated.outputRoot()).isEqualTo("SERVER_MANAGED");
        assertThat(generated.toString()).doesNotContain(OUTPUT_ROOT.toAbsolutePath().normalize().toString());
        assertThat(OUTPUT_ROOT.resolve("backend/src/main/java/com/enterprise/auth/platform/generated/modules/orderGen/infrastructure/entity/OrderGenEntity.java"))
                .exists();
        assertThatThrownBy(() -> codegenApplicationService.generate(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生成文件已存在");
    }

    @Test
    void shouldRegisterAllGeneratedMenuPermissions() {
        var generated = codegenApplicationService.generate(new CodegenCommand(
                TABLE_NAME,
                "orderGen",
                "com.enterprise.auth.platform.generated",
                "OrderGen",
                true,
                true,
                false,
                null,
                true
        ));

        assertThat(generated.registeredResourceKeys())
                .containsExactly("orderGen:page", "orderGen:get", "orderGen:add", "orderGen:edit", "orderGen:del");
        assertThat(jdbcTemplate.queryForList(
                "SELECT permission FROM sys_menu WHERE del_flag = '0' AND type = '1' AND permission LIKE 'orderGen:%' ORDER BY sort",
                String.class
        )).containsExactly("orderGen:page", "orderGen:get", "orderGen:add", "orderGen:edit", "orderGen:del");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE del_flag = '0' AND type = '0' AND path = '/platform/generated/orderGen'",
                Long.class
        )).isEqualTo(1L);
    }

    @Test
    void shouldRejectSelectedFilesOutsideCurrentPreview() {
        var command = new CodegenCommand(
                TABLE_NAME,
                "orderGen",
                "com.enterprise.auth.platform.generated",
                "OrderGen",
                true,
                true,
                false,
                List.of("backend/src/main/java/com/enterprise/auth/platform/generated/modules/orderGen/infrastructure/entity/OrderGenEntity.java", "../escape.txt"),
                false
        );

        assertThatThrownBy(() -> codegenApplicationService.download(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("选择的生成文件不存在或不属于本次生成结果");
    }

    @Test
    void shouldExposeZipDownloadAsAttachment() {
        var controller = new CodegenController(codegenApplicationService, null, null);
        var response = controller.download(new CodegenRequest(
                TABLE_NAME,
                "orderGen",
                "com.enterprise.auth.platform.generated",
                "OrderGen",
                true,
                true,
                false,
                null,
                false
        ));

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("filename*=UTF-8''orderGen-OrderGen.zip");
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void shouldImportTableAndUsePersistedColumnConfigInPreview() {
        var dataSource = codegenMetadataService.dataSources().stream()
                .filter(item -> "LOCAL".equals(item.jdbcUrl()))
                .findFirst()
                .orElseThrow();

        var imported = codegenMetadataService.importTables(new CodegenMetadataDtos.ImportTableRequest(
                dataSource.id(),
                List.of(TABLE_NAME),
                "com.enterprise.auth.platform.generated",
                "tester"
        ));
        assertThat(imported).hasSize(1);

        var detail = codegenMetadataService.tableConfig(imported.get(0).id());
        var updatedColumns = detail.columns().stream()
                .map(column -> switch (column.columnName()) {
                    case "order_no" -> new CodegenMetadataDtos.ColumnConfigView(
                            column.id(),
                            column.columnName(),
                            "业务订单号",
                            column.columnType(),
                            column.dataType(),
                            column.javaType(),
                            "bizOrderNo",
                            column.primaryKey(),
                            true,
                            true,
                            false,
                            true,
                            true,
                            "LIKE",
                            "textarea",
                            "order_status",
                            column.sort());
                    case "amount" -> new CodegenMetadataDtos.ColumnConfigView(
                            column.id(),
                            column.columnName(),
                            "下单金额",
                            column.columnType(),
                            column.dataType(),
                            column.javaType(),
                            column.javaField(),
                            column.primaryKey(),
                            false,
                            false,
                            true,
                            false,
                            false,
                            "EQ",
                            "number",
                            column.dictType(),
                            column.sort());
                    case "enabled" -> new CodegenMetadataDtos.ColumnConfigView(
                            column.id(),
                            column.columnName(),
                            "启用状态",
                            column.columnType(),
                            column.dataType(),
                            "Boolean",
                            column.javaField(),
                            column.primaryKey(),
                            true,
                            true,
                            true,
                            true,
                            false,
                            "EQ",
                            "select",
                            column.dictType(),
                            column.sort());
                    case "created_at" -> new CodegenMetadataDtos.ColumnConfigView(
                            column.id(),
                            column.columnName(),
                            "创建时间",
                            column.columnType(),
                            column.dataType(),
                            column.javaType(),
                            column.javaField(),
                            column.primaryKey(),
                            false,
                            false,
                            false,
                            false,
                            true,
                            "BETWEEN",
                            "datetime",
                            column.dictType(),
                            column.sort());
                    default -> column;
                })
                .toList();
        codegenMetadataService.updateColumns(imported.get(0).id(), new CodegenMetadataDtos.UpdateColumnsRequest(updatedColumns));

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

        assertThat(fileContent(preview, "OrderGenEntity.java"))
                .contains("private String bizOrderNo;")
                .contains("private Boolean enabled;")
                .contains("@TableField(value = \"created_at\", fill = FieldFill.INSERT)");

        assertThat(fileContent(preview, "OrderGenCreateRequest.java"))
                .contains("public record OrderGenCreateRequest")
                .contains("@NotBlank String bizOrderNo")
                .contains("@NotNull Boolean enabled")
                .doesNotContain("amount")
                .doesNotContain("createdAt");
        assertThat(fileContent(preview, "OrderGenUpdateRequest.java"))
                .contains("public record OrderGenUpdateRequest")
                .contains("java.math.BigDecimal amount")
                .contains("Boolean enabled")
                .doesNotContain("bizOrderNo")
                .doesNotContain("createdAt");
        assertThat(fileContent(preview, "OrderGenQueryRequest.java"))
                .contains("public record OrderGenQueryRequest")
                .contains("String bizOrderNo")
                .contains("java.time.LocalDateTime createdAtStart")
                .contains("java.time.LocalDateTime createdAtEnd")
                .doesNotContain("amount")
                .doesNotContain("enabled");

        assertThat(fileContent(preview, "OrderGenApplicationService.java"))
                .contains("query.like(OrderGenEntity::getBizOrderNo, request.bizOrderNo())")
                .contains("query.ge(OrderGenEntity::getCreatedAt, request.createdAtStart())")
                .contains("query.le(OrderGenEntity::getCreatedAt, request.createdAtEnd())")
                .contains("entity.setBizOrderNo(request.bizOrderNo());")
                .contains("entity.setAmount(request.amount());")
                .doesNotContain("StringUtils.hasText(keyword)");

        assertThat(fileContent(preview, "frontend/src/types/orderGen.ts"))
                .contains("bizOrderNo: string")
                .contains("enabled: boolean")
                .contains("export interface OrderGenCreateRequest")
                .contains("bizOrderNo: string")
                .doesNotContain("amount: number\n}\n\nexport interface OrderGenUpdateRequest")
                .contains("export interface OrderGenQueryParams")
                .contains("createdAtStart?: string | null")
                .contains("createdAtEnd?: string | null");

        var view = fileContent(preview, "frontend/src/views/generated/OrderGenView.vue");
        assertThat(view)
                .contains("<el-table-column prop=\"bizOrderNo\"")
                .contains("<el-table-column prop=\"enabled\"")
                .doesNotContain("<el-table-column prop=\"amount\"")
                .contains("<el-input v-model=\"query.bizOrderNo\"")
                .contains("<el-date-picker v-model=\"query.createdAtStart\"")
                .doesNotContain("v-model=\"query.amount\"")
                .contains("v-if=\"editingId === null\" label=\"业务订单号\"")
                .contains("v-if=\"editingId !== null\" label=\"下单金额\"")
                .contains("const createRules = reactive<FormRules>({")
                .contains("bizOrderNo: [{ required: true")
                .contains("const updateRules = reactive<FormRules>({")
                .doesNotContain("dictApi");
        assertThat(view.substring(view.indexOf("function toCreatePayload"), view.indexOf("function toUpdatePayload")))
                .contains("bizOrderNo: form.bizOrderNo")
                .contains("enabled: form.enabled")
                .doesNotContain("amount");
        assertThat(view.substring(view.indexOf("function toUpdatePayload")))
                .contains("amount: form.amount")
                .contains("enabled: form.enabled")
                .doesNotContain("bizOrderNo: form.bizOrderNo");
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

    private void cleanupGeneratedMenus() {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE path = ? OR permission LIKE ?)",
                "/platform/generated/orderGen", "orderGen:%");
        jdbcTemplate.update("DELETE FROM sys_menu WHERE path = ? OR permission LIKE ?", "/platform/generated/orderGen", "orderGen:%");
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