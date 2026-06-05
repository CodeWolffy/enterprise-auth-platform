package com.enterprise.auth.platform.modules.codegen.application;

import com.enterprise.auth.platform.common.audit.AuditEventPublisher;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodegenApplicationService {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    private static final Set<String> SYSTEM_COLUMNS = Set.of("id", "tenant_id", "created_by", "updated_by", "deleted", "created_at", "updated_at");
    private static final String DEFAULT_PACKAGE = "com.enterprise.auth.platform.generated";

    private final JdbcTemplate jdbcTemplate;
    private final AuditEventPublisher auditEventPublisher;
    private final Path outputRoot;
    private final CodegenTemplateService templateService;
    private final CodegenResourceRegistrationService registrationService;

    public CodegenApplicationService(
            DataSource dataSource,
            AuditEventPublisher auditEventPublisher,
            @Value("${platform.codegen.output-root:target/generated-codegen}") String outputRoot,
            CodegenTemplateService templateService,
            CodegenResourceRegistrationService registrationService
    ) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.auditEventPublisher = auditEventPublisher;
        this.outputRoot = Path.of(outputRoot).toAbsolutePath().normalize();
        this.templateService = templateService;
        this.registrationService = registrationService;
    }

    @Transactional(readOnly = true)
    public PageResult<CodegenTableView> tables(String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<Object> params = new ArrayList<>();
        String filterSql = " WHERE table_schema = DATABASE()";
        if (!normalizedKeyword.isBlank()) {
            filterSql += " AND (table_name LIKE ? OR table_comment LIKE ?)";
            String like = "%" + normalizedKeyword + "%";
            params.add(like);
            params.add(like);
        }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables" + filterSql, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add((safePage - 1) * safeSize);
        pageParams.add(safeSize);
        List<CodegenTableView> records = jdbcTemplate.query(
                "SELECT table_name, table_comment, engine, table_rows, data_length, index_length, create_time, update_time "
                        + "FROM information_schema.tables"
                        + filterSql
                        + " ORDER BY table_name LIMIT ?, ?",
                (rs, rowNum) -> tableView(rs),
                pageParams.toArray()
        );
        return PageResult.of(total == null ? 0 : total, safePage, safeSize, records);
    }

    @Transactional(readOnly = true)
    public CodegenTableDetailView table(String tableName) {
        return new CodegenTableDetailView(requireTable(tableName), columns(tableName));
    }

    @Transactional(readOnly = true)
    public CodegenPreviewResult preview(CodegenCommand command) {
        CodegenModel model = buildModel(command);
        return new CodegenPreviewResult(
                model.tableName(),
                model.moduleName(),
                model.className(),
                outputRoot.toString(),
                renderFiles(model, command.includeBackend(), command.includeFrontend()),
                command.selectedFiles(),
                command.autoRegister()
        );
    }

    @Transactional
    public CodegenGenerateResult generate(CodegenCommand command) {
        CodegenPreviewResult preview = preview(command);
        List<String> selectedFiles = resolveSelectedFiles(command.selectedFiles(), preview.files(), "至少选择一个生成文件");
        List<String> written = new ArrayList<>();
        for (CodegenFilePreview file : preview.files()) {
            if (!selectedFiles.contains(file.path())) {
                continue;
            }
            Path target = safeTarget(file.path());
            if (Files.exists(target) && !command.overwrite()) {
                throw new BusinessException("CONFLICT", "生成文件已存在，请启用覆盖或调整模块名：" + file.path());
            }
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.content(), StandardCharsets.UTF_8);
                written.add(outputRoot.relativize(target).toString().replace('\\', '/'));
            } catch (IOException ex) {
                throw new BusinessException("CODEGEN_WRITE_FAILED", "生成文件写入失败：" + file.path());
            }
        }
        List<String> registered = command.autoRegister()
                ? registrationService.register(preview.moduleName(), preview.className())
                : List.of();
        auditEventPublisher.publish("CODEGEN_GENERATED", "system", TenantContext.getTenantId(), Map.of(
                "tableName", preview.tableName(),
                "moduleName", preview.moduleName(),
                "files", written,
                "registeredPermissions", registered
        ));
        return new CodegenGenerateResult(preview.tableName(), preview.moduleName(), outputRoot.toString(), written, registered);
    }

    @Transactional(readOnly = true)
    public CodegenArtifactDownload download(CodegenCommand command) {
        CodegenPreviewResult preview = preview(command);
        List<String> selectedFiles = resolveSelectedFiles(command.selectedFiles(), preview.files(), "至少选择一个导出文件");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
                for (CodegenFilePreview file : preview.files()) {
                    if (!selectedFiles.contains(file.path())) {
                        continue;
                    }
                    zip.putNextEntry(new ZipEntry(file.path()));
                    zip.write(file.content().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return new CodegenArtifactDownload(
                    preview.moduleName() + "-" + preview.className() + ".zip",
                    "application/zip",
                    buffer.toByteArray()
            );
        } catch (IOException ex) {
            throw new BusinessException("CODEGEN_PACKAGE_FAILED", "生成产物打包失败");
        }
    }

    private List<String> resolveSelectedFiles(List<String> selectedFiles, List<CodegenFilePreview> files, String emptyMessage) {
        if (selectedFiles == null) {
            return files.stream().map(CodegenFilePreview::path).toList();
        }
        Set<String> allowedPaths = files.stream()
                .map(CodegenFilePreview::path)
                .collect(java.util.stream.Collectors.toSet());
        List<String> resolved = selectedFiles.stream()
                .filter(allowedPaths::contains)
                .distinct()
                .toList();
        if (resolved.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", emptyMessage);
        }
        return resolved;
    }

    private CodegenModel buildModel(CodegenCommand command) {
        if (command == null) {
            throw new BusinessException("VALIDATION_ERROR", "生成参数不能为空");
        }
        CodegenTableView table = requireTable(command.tableName());
        List<CodegenColumnView> columns = columns(table.tableName());
        if (columns.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "数据表没有可生成字段");
        }
        String moduleName = normalizeIdentifier(command.moduleName(), "moduleName", toCamel(table.tableName(), false));
        String className = normalizeClassName(command.className(), toCamel(stripPrefix(table.tableName()), true));
        String packageName = normalizePackage(command.packageName());
        CodegenColumnView primaryKey = columns.stream().filter(CodegenColumnView::primaryKey).findFirst().orElse(columns.get(0));
        List<CodegenColumnView> editableColumns = columns.stream()
                .filter(column -> !column.columnName().equals(primaryKey.columnName()))
                .filter(column -> !SYSTEM_COLUMNS.contains(column.columnName().toLowerCase(Locale.ROOT)))
                .toList();
        if (editableColumns.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "数据表没有可写业务字段，无法生成完整 CRUD");
        }
        boolean includeBackend = command.includeBackend();
        boolean includeFrontend = command.includeFrontend();
        if (!includeBackend && !includeFrontend) {
            throw new BusinessException("VALIDATION_ERROR", "至少选择一种生成范围");
        }
        return new CodegenModel(
                table.tableName(),
                moduleName,
                packageName,
                className,
                Character.toLowerCase(className.charAt(0)) + className.substring(1),
                toKebab(className),
                table.tableComment() == null || table.tableComment().isBlank() ? className : table.tableComment(),
                primaryKey.columnName(),
                primaryKey.javaField(),
                primaryKey.javaType(),
                columns,
                editableColumns
        );
    }

    private CodegenTableView requireTable(String tableName) {
        String safeTableName = normalizeTableName(tableName);
        List<CodegenTableView> tables = jdbcTemplate.query(
                "SELECT table_name, table_comment, engine, table_rows, data_length, index_length, create_time, update_time "
                        + "FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                (rs, rowNum) -> tableView(rs),
                safeTableName
        );
        if (tables.isEmpty()) {
            throw new BusinessException("NOT_FOUND", "数据表不存在：" + safeTableName);
        }
        return tables.get(0);
    }

    private List<CodegenColumnView> columns(String tableName) {
        String safeTableName = normalizeTableName(tableName);
        return jdbcTemplate.query(
                "SELECT column_name, data_type, column_type, is_nullable, column_key, extra, column_default, column_comment "
                        + "FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? ORDER BY ordinal_position",
                (rs, rowNum) -> columnView(rs),
                safeTableName
        );
    }

    private List<CodegenFilePreview> renderFiles(CodegenModel model, boolean includeBackend, boolean includeFrontend) {
        List<CodegenFilePreview> files = new ArrayList<>();
        if (includeBackend) {
            files.add(new CodegenFilePreview(backendPath(model, "infrastructure/entity", model.className() + "Entity.java"), "java", renderWithTemplate("java", backendPath(model, "infrastructure/entity", model.className() + "Entity.java"), renderEntity(model), templateVariables(model))));
            files.add(new CodegenFilePreview(backendPath(model, "infrastructure/mapper", model.className() + "Mapper.java"), "java", renderWithTemplate("java", backendPath(model, "infrastructure/mapper", model.className() + "Mapper.java"), renderMapper(model), templateVariables(model))));
            files.add(new CodegenFilePreview(backendPath(model, "interfaces", model.className() + "CrudRequest.java"), "java", renderWithTemplate("java", backendPath(model, "interfaces", model.className() + "CrudRequest.java"), renderCrudRequest(model), templateVariables(model))));
            files.add(new CodegenFilePreview(backendPath(model, "application", model.className() + "ApplicationService.java"), "java", renderWithTemplate("java", backendPath(model, "application", model.className() + "ApplicationService.java"), renderService(model), templateVariables(model))));
            files.add(new CodegenFilePreview(backendPath(model, "interfaces", model.className() + "Controller.java"), "java", renderWithTemplate("java", backendPath(model, "interfaces", model.className() + "Controller.java"), renderController(model), templateVariables(model))));
        }
        if (includeFrontend) {
            files.add(new CodegenFilePreview("frontend/src/types/" + model.moduleName() + ".ts", "typescript", renderWithTemplate("typescript", model.moduleName() + ".ts", renderTypes(model), templateVariables(model))));
            files.add(new CodegenFilePreview("frontend/src/api/modules/" + model.moduleName() + ".ts", "typescript", renderWithTemplate("typescript", model.moduleName() + ".ts", renderApi(model), templateVariables(model))));
            files.add(new CodegenFilePreview("frontend/src/views/generated/" + model.className() + "View.vue", "vue", renderWithTemplate("vue", model.className() + "View.vue", renderView(model), templateVariables(model))));
        }
        return files;
    }

    private String renderWithTemplate(String language, String path, String defaultContent, Map<String, Object> variables) {
        Map<String, Object> renderVariables = new LinkedHashMap<>(variables);
        renderVariables.put("defaultContent", defaultContent);
        return templateService.renderBody(language, path, defaultContent, renderVariables);
    }

    private Map<String, Object> templateVariables(CodegenModel model) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("tableName", model.tableName());
        variables.put("moduleName", model.moduleName());
        variables.put("className", model.className());
        variables.put("lowerClassName", model.lowerClassName());
        variables.put("kebabName", model.kebabName());
        variables.put("title", model.title());
        variables.put("packageName", model.packageName());
        variables.put("primaryKeyColumn", model.primaryKeyColumn());
        variables.put("primaryKeyField", model.primaryKeyField());
        variables.put("primaryKeyJavaType", model.primaryKeyJavaType());
        variables.put("generatedAt", Instant.now().toString());
        return variables;
    }

    private String renderEntity(CodegenModel model) {
        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(model.packageName()).append(".modules.").append(model.moduleName()).append(".infrastructure.entity;\n\n");
        builder.append("import com.baomidou.mybatisplus.annotation.FieldFill;\n");
        builder.append("import com.baomidou.mybatisplus.annotation.IdType;\n");
        builder.append("import com.baomidou.mybatisplus.annotation.TableField;\n");
        builder.append("import com.baomidou.mybatisplus.annotation.TableId;\n");
        builder.append("import com.baomidou.mybatisplus.annotation.TableLogic;\n");
        builder.append("import com.baomidou.mybatisplus.annotation.TableName;\n");
        builder.append("import java.time.LocalDateTime;\n");
        builder.append("import lombok.Data;\n\n");
        builder.append("@Data\n@TableName(\"").append(model.tableName()).append("\")\n");
        builder.append("public class ").append(model.className()).append("Entity {\n\n");
        for (CodegenColumnView column : model.columns()) {
            if (isPrimaryKey(model, column) && column.autoIncrement()) {
                builder.append("    @TableId(value = \"").append(column.columnName()).append("\", type = IdType.AUTO)\n");
            } else if (isPrimaryKey(model, column)) {
                builder.append("    @TableId(\"").append(column.columnName()).append("\")\n");
            } else if (isColumn(column, "deleted")) {
                builder.append("    @TableLogic\n");
            } else {
                String fill = fieldFill(column);
                if (fill == null) {
                    builder.append("    @TableField(\"").append(column.columnName()).append("\")\n");
                } else {
                    builder.append("    @TableField(value = \"").append(column.columnName()).append("\", fill = FieldFill.").append(fill).append(")\n");
                }
            }
            builder.append("    private ").append(column.javaType()).append(' ').append(column.javaField()).append(";\n\n");
        }
        builder.append("}\n");
        return builder.toString();
    }

    private String renderMapper(CodegenModel model) {
        return "package " + model.packageName() + ".modules." + model.moduleName() + ".infrastructure.mapper;\n\n"
                + "import com.baomidou.mybatisplus.core.mapper.BaseMapper;\n"
                + "import " + model.packageName() + ".modules." + model.moduleName() + ".infrastructure.entity." + model.className() + "Entity;\n"
                + "import org.apache.ibatis.annotations.Mapper;\n\n"
                + "@Mapper\n"
                + "public interface " + model.className() + "Mapper extends BaseMapper<" + model.className() + "Entity> {\n"
                + "}\n";
    }

    private String renderCrudRequest(CodegenModel model) {
        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(model.packageName()).append(".modules.").append(model.moduleName()).append(".interfaces;\n\n");
        builder.append("import io.swagger.v3.oas.annotations.media.Schema;\n");
        builder.append("import jakarta.validation.constraints.NotBlank;\n");
        builder.append("import jakarta.validation.constraints.NotNull;\n");
        builder.append("import java.time.LocalDateTime;\n\n");
        builder.append("@Schema(description = \"").append(escapeJava(model.title())).append("新增或修改请求\")\n");
        builder.append("public record ").append(model.className()).append("CrudRequest(");
        if (model.editableColumns().isEmpty()) {
            builder.append(") {\n}\n");
            return builder.toString();
        }
        builder.append("\n");
        for (int i = 0; i < model.editableColumns().size(); i++) {
            CodegenColumnView column = model.editableColumns().get(i);
            boolean required = requestRequired(column);
            builder.append("        @Schema(description = \"").append(escapeJava(columnLabel(column))).append("\"");
            if (required) {
                builder.append(", requiredMode = Schema.RequiredMode.REQUIRED");
            }
            builder.append(") ");
            if (required) {
                builder.append("String".equals(column.javaType()) ? "@NotBlank " : "@NotNull ");
            }
            builder.append(column.javaType()).append(' ').append(column.javaField());
            builder.append(i + 1 == model.editableColumns().size() ? "\n" : ",\n");
        }
        builder.append(") {\n}\n");
        return builder.toString();
    }

    private String renderService(CodegenModel model) {
        String entity = model.className() + "Entity";
        String request = model.className() + "CrudRequest";
        String mapper = model.className() + "Mapper";
        return "package " + model.packageName() + ".modules." + model.moduleName() + ".application;\n\n"
                + "import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;\n"
                + "import com.enterprise.auth.platform.common.context.TenantContext;\n"
                + "import com.enterprise.auth.platform.common.exception.BusinessException;\n"
                + "import com.enterprise.auth.platform.common.web.PageResult;\n"
                + "import " + model.packageName() + ".modules." + model.moduleName() + ".infrastructure.entity." + entity + ";\n"
                + "import " + model.packageName() + ".modules." + model.moduleName() + ".infrastructure.mapper." + mapper + ";\n"
                + "import " + model.packageName() + ".modules." + model.moduleName() + ".interfaces." + request + ";\n"
                + "import java.util.List;\n"
                + "import org.springframework.stereotype.Service;\n"
                + "import org.springframework.transaction.annotation.Transactional;\n"
                + "import org.springframework.util.StringUtils;\n\n"
                + "@Service\n"
                + "public class " + model.className() + "ApplicationService {\n\n"
                + "    private final " + mapper + " mapper;\n\n"
                + "    public " + model.className() + "ApplicationService(" + mapper + " mapper) {\n"
                + "        this.mapper = mapper;\n"
                + "    }\n\n"
                + "    @Transactional(readOnly = true)\n"
                + "    public PageResult<" + entity + "> page(String keyword, int page, int size) {\n"
                + "        int safePage = Math.max(page, 1);\n"
                + "        int safeSize = Math.min(Math.max(size, 1), 100);\n"
                + "        LambdaQueryWrapper<" + entity + "> countQuery = baseQuery(keyword);\n"
                + "        Long total = mapper.selectCount(countQuery);\n"
                + "        if (total == null || total == 0) {\n"
                + "            return PageResult.empty(safePage, safeSize);\n"
                + "        }\n"
                + "        LambdaQueryWrapper<" + entity + "> listQuery = baseQuery(keyword);\n"
                + "        applyDefaultOrder(listQuery);\n"
                + "        listQuery.last(\"limit \" + ((safePage - 1) * safeSize) + \",\" + safeSize);\n"
                + "        return PageResult.of(total, safePage, safeSize, mapper.selectList(listQuery));\n"
                + "    }\n\n"
                + "    @Transactional(readOnly = true)\n"
                + "    public " + entity + " detail(" + model.primaryKeyJavaType() + " id) {\n"
                + "        return getExisting(id);\n"
                + "    }\n\n"
                + "    @Transactional\n"
                + "    public " + entity + " create(" + request + " request) {\n"
                + "        " + entity + " entity = new " + entity + "();\n"
                + renderTenantAssignment(model)
                + renderApplyRequest(model)
                + "        mapper.insert(entity);\n"
                + "        return entity;\n"
                + "    }\n\n"
                + "    @Transactional\n"
                + "    public " + entity + " update(" + model.primaryKeyJavaType() + " id, " + request + " request) {\n"
                + "        " + entity + " entity = getExisting(id);\n"
                + renderApplyRequest(model)
                + "        mapper.updateById(entity);\n"
                + "        return entity;\n"
                + "    }\n\n"
                + "    @Transactional\n"
                + "    public void delete(" + model.primaryKeyJavaType() + " id) {\n"
                + "        " + entity + " entity = getExisting(id);\n"
                + "        mapper.deleteById(entity.get" + upperFirst(model.primaryKeyField()) + "());\n"
                + "    }\n\n"
                + "    private " + entity + " getExisting(" + model.primaryKeyJavaType() + " id) {\n"
                + "        " + entity + " entity = mapper.selectOne(baseQuery(null)\n"
                + "                .eq(" + entity + "::get" + upperFirst(model.primaryKeyField()) + ", id)\n"
                + "                .last(\"limit 1\"));\n"
                + "        if (entity == null) {\n"
                + "            throw new BusinessException(\"NOT_FOUND\", \"数据不存在\");\n"
                + "        }\n"
                + "        return entity;\n"
                + "    }\n\n"
                + "    private LambdaQueryWrapper<" + entity + "> baseQuery(String keyword) {\n"
                + "        LambdaQueryWrapper<" + entity + "> query = new LambdaQueryWrapper<>();\n"
                + renderTenantFilter(model)
                + renderDeletedFilter(model)
                + renderKeywordFilter(model)
                + "        return query;\n"
                + "    }\n\n"
                + "    private void applyDefaultOrder(LambdaQueryWrapper<" + entity + "> query) {\n"
                + renderDefaultOrder(model)
                + "    }\n\n"
                + "    private String currentTenantId() {\n"
                + "        String tenantId = TenantContext.getTenantId();\n"
                + "        return StringUtils.hasText(tenantId) ? tenantId : \"platform\";\n"
                + "    }\n"
                + "}\n";
    }

    private String renderController(CodegenModel model) {
        String entity = model.className() + "Entity";
        String request = model.className() + "CrudRequest";
        return "package " + model.packageName() + ".modules." + model.moduleName() + ".interfaces;\n\n"
                + "import cn.dev33.satoken.annotation.SaCheckPermission;\n"
                + "import com.enterprise.auth.platform.common.web.ApiResponse;\n"
                + "import com.enterprise.auth.platform.common.web.PageResult;\n"
                + "import " + model.packageName() + ".modules." + model.moduleName() + ".application." + model.className() + "ApplicationService;\n"
                + "import " + model.packageName() + ".modules." + model.moduleName() + ".infrastructure.entity." + entity + ";\n"
                + "import io.swagger.v3.oas.annotations.Operation;\n"
                + "import io.swagger.v3.oas.annotations.Parameter;\n"
                + "import io.swagger.v3.oas.annotations.tags.Tag;\n"
                + "import jakarta.validation.Valid;\n"
                + "import org.springframework.web.bind.annotation.DeleteMapping;\n"
                + "import org.springframework.web.bind.annotation.GetMapping;\n"
                + "import org.springframework.web.bind.annotation.PathVariable;\n"
                + "import org.springframework.web.bind.annotation.PostMapping;\n"
                + "import org.springframework.web.bind.annotation.PutMapping;\n"
                + "import org.springframework.web.bind.annotation.RequestBody;\n"
                + "import org.springframework.web.bind.annotation.RequestMapping;\n"
                + "import org.springframework.web.bind.annotation.RequestParam;\n"
                + "import org.springframework.web.bind.annotation.RestController;\n\n"
                + "@Tag(name = \"" + escapeJava(model.title()) + "\")\n"
                + "@RestController\n"
                + "@RequestMapping(\"/api/generated/" + model.kebabName() + "\")\n"
                + "public class " + model.className() + "Controller {\n\n"
                + "    private final " + model.className() + "ApplicationService service;\n\n"
                + "    public " + model.className() + "Controller(" + model.className() + "ApplicationService service) {\n"
                + "        this.service = service;\n"
                + "    }\n\n"
                + "    @Operation(summary = \"分页查询" + escapeJava(model.title()) + "\")\n"
                + "    @GetMapping\n"
                + "    @SaCheckPermission(\"" + model.moduleName() + ":read\")\n"
                + "    public ApiResponse<PageResult<" + entity + ">> page(\n"
                + "            @Parameter(description = \"关键字\") @RequestParam(required = false) String keyword,\n"
                + "            @Parameter(description = \"页码\") @RequestParam(defaultValue = \"1\") int page,\n"
                + "            @Parameter(description = \"每页数量\") @RequestParam(defaultValue = \"20\") int size\n"
                + "    ) {\n"
                + "        return ApiResponse.ok(service.page(keyword, page, size));\n"
                + "    }\n\n"
                + "    @Operation(summary = \"查询" + escapeJava(model.title()) + "详情\")\n"
                + "    @GetMapping(\"/{id}\")\n"
                + "    @SaCheckPermission(\"" + model.moduleName() + ":read\")\n"
                + "    public ApiResponse<" + entity + "> detail(@Parameter(description = \"主键\") @PathVariable " + model.primaryKeyJavaType() + " id) {\n"
                + "        return ApiResponse.ok(service.detail(id));\n"
                + "    }\n\n"
                + "    @Operation(summary = \"新增" + escapeJava(model.title()) + "\")\n"
                + "    @PostMapping\n"
                + "    @SaCheckPermission(\"" + model.moduleName() + ":write\")\n"
                + "    public ApiResponse<" + entity + "> create(@Valid @RequestBody " + request + " request) {\n"
                + "        return ApiResponse.ok(service.create(request));\n"
                + "    }\n\n"
                + "    @Operation(summary = \"修改" + escapeJava(model.title()) + "\")\n"
                + "    @PutMapping(\"/{id}\")\n"
                + "    @SaCheckPermission(\"" + model.moduleName() + ":write\")\n"
                + "    public ApiResponse<" + entity + "> update(\n"
                + "            @Parameter(description = \"主键\") @PathVariable " + model.primaryKeyJavaType() + " id,\n"
                + "            @Valid @RequestBody " + request + " request\n"
                + "    ) {\n"
                + "        return ApiResponse.ok(service.update(id, request));\n"
                + "    }\n\n"
                + "    @Operation(summary = \"删除" + escapeJava(model.title()) + "\")\n"
                + "    @DeleteMapping(\"/{id}\")\n"
                + "    @SaCheckPermission(\"" + model.moduleName() + ":write\")\n"
                + "    public ApiResponse<Void> delete(@Parameter(description = \"主键\") @PathVariable " + model.primaryKeyJavaType() + " id) {\n"
                + "        service.delete(id);\n"
                + "        return ApiResponse.ok();\n"
                + "    }\n"
                + "}\n";
    }

    private String renderTypes(CodegenModel model) {
        StringBuilder builder = new StringBuilder();
        builder.append("export interface ").append(model.className()).append("View {\n");
        for (CodegenColumnView column : model.columns()) {
            boolean optional = column.nullable() && !isPrimaryKey(model, column);
            builder.append("  ").append(column.javaField()).append(optional ? "?: " : ": ").append(column.tsType()).append(optional ? " | null" : "").append("\n");
        }
        builder.append("}\n\n");
        builder.append("export interface ").append(model.className()).append("CrudRequest {\n");
        for (CodegenColumnView column : model.editableColumns()) {
            builder.append("  ").append(column.javaField()).append("?: ").append(column.tsType()).append(" | null\n");
        }
        builder.append("}\n\n");
        builder.append("export interface ").append(model.className()).append("Page {\n");
        builder.append("  total: number\n  page: number\n  size: number\n  records: ").append(model.className()).append("View[]\n}\n");
        return builder.toString();
    }

    private String renderApi(CodegenModel model) {
        return "import { http } from '../http'\n"
                + "import type { ApiResponse } from '@/types/api'\n"
                + "import type { " + model.className() + "CrudRequest, " + model.className() + "Page, " + model.className() + "View } from '@/types/" + model.moduleName() + "'\n\n"
                + "export interface " + model.className() + "QueryParams {\n"
                + "  keyword?: string\n"
                + "  page?: number\n"
                + "  size?: number\n}\n\n"
                + "export async function query" + model.className() + "Page(params?: " + model.className() + "QueryParams) {\n"
                + "  const { data } = await http.get<ApiResponse<" + model.className() + "Page>>('/api/generated/" + model.kebabName() + "', { params })\n"
                + "  return data.data\n"
                + "}\n\n"
                + "export async function get" + model.className() + "(id: " + tsScalarType(model.primaryKeyJavaType()) + ") {\n"
                + "  const { data } = await http.get<ApiResponse<" + model.className() + "View>>(`/api/generated/" + model.kebabName() + "/${id}`)\n"
                + "  return data.data\n"
                + "}\n\n"
                + "export async function create" + model.className() + "(payload: " + model.className() + "CrudRequest) {\n"
                + "  const { data } = await http.post<ApiResponse<" + model.className() + "View>>('/api/generated/" + model.kebabName() + "', payload)\n"
                + "  return data.data\n"
                + "}\n\n"
                + "export async function update" + model.className() + "(id: " + tsScalarType(model.primaryKeyJavaType()) + ", payload: " + model.className() + "CrudRequest) {\n"
                + "  const { data } = await http.put<ApiResponse<" + model.className() + "View>>(`/api/generated/" + model.kebabName() + "/${id}`, payload)\n"
                + "  return data.data\n"
                + "}\n\n"
                + "export async function delete" + model.className() + "(id: " + tsScalarType(model.primaryKeyJavaType()) + ") {\n"
                + "  await http.delete(`/api/generated/" + model.kebabName() + "/${id}`)\n"
                + "}\n";
    }

    private String renderView(CodegenModel model) {
        return "<template>\n"
                + "  <div class=\"panel-stack\">\n"
                + "    <section class=\"dashboard-panel\">\n"
                + "      <div class=\"panel-head\">\n"
                + "        <div>\n"
                + "          <span class=\"eyebrow\">Generated CRUD</span>\n"
                + "          <h3>" + escapeVue(model.title()) + "</h3>\n"
                + "        </div>\n"
                + "        <div class=\"panel-actions\">\n"
                + "          <el-button :loading=\"loading\" @click=\"load\">刷新</el-button>\n"
                + "          <el-button v-permission=\"'" + model.moduleName() + ":write'\" type=\"primary\" @click=\"openForm()\">新增</el-button>\n"
                + "        </div>\n"
                + "      </div>\n\n"
                + "      <AdvancedSearch @search=\"handleSearch\" @reset=\"resetSearch\">\n"
                + "        <el-form-item label=\"关键字\">\n"
                + "          <el-input v-model=\"keyword\" placeholder=\"搜索关键字段\" clearable />\n"
                + "        </el-form-item>\n"
                + "      </AdvancedSearch>\n\n"
                + "      <el-table v-loading=\"loading\" :data=\"records\" stripe>\n"
                + renderVueColumns(model)
                + "        <el-table-column fixed=\"right\" label=\"操作\" width=\"180\">\n"
                + "          <template #default=\"{ row }\">\n"
                + "            <el-button link type=\"primary\" @click=\"openDetail(row)\">详情</el-button>\n"
                + "            <el-button v-permission=\"'" + model.moduleName() + ":write'\" link type=\"primary\" @click=\"openForm(row)\">编辑</el-button>\n"
                + "            <el-button v-permission=\"'" + model.moduleName() + ":write'\" link type=\"danger\" @click=\"remove(row)\">删除</el-button>\n"
                + "          </template>\n"
                + "        </el-table-column>\n"
                + "        <template #empty><el-empty description=\"暂无数据\" /></template>\n"
                + "      </el-table>\n\n"
                + "      <div class=\"pagination-wrap\">\n"
                + "        <el-pagination\n"
                + "          v-model:current-page=\"page\"\n"
                + "          v-model:page-size=\"size\"\n"
                + "          :page-sizes=\"[10, 20, 50, 100]\"\n"
                + "          layout=\"total, sizes, prev, pager, next\"\n"
                + "          :total=\"total\"\n"
                + "          @size-change=\"handleSizeChange\"\n"
                + "          @current-change=\"handleCurrentChange\"\n"
                + "        />\n"
                + "      </div>\n"
                + "    </section>\n\n"
                + "    <el-drawer v-model=\"detailVisible\" title=\"详情\" size=\"560px\">\n"
                + "      <el-descriptions v-if=\"detailItem\" :column=\"1\" border>\n"
                + renderVueDescriptions(model)
                + "      </el-descriptions>\n"
                + "    </el-drawer>\n\n"
                + "    <el-dialog v-model=\"formVisible\" :title=\"editingId === null ? '新增' : '编辑'\" width=\"560px\">\n"
                + "      <el-form ref=\"formRef\" label-position=\"top\" :model=\"form\" :rules=\"rules\">\n"
                + renderVueFormItems(model)
                + "      </el-form>\n"
                + "      <template #footer>\n"
                + "        <el-button @click=\"formVisible = false\">取消</el-button>\n"
                + "        <el-button v-permission=\"'" + model.moduleName() + ":write'\" type=\"primary\" @click=\"submit\">保存</el-button>\n"
                + "      </template>\n"
                + "    </el-dialog>\n"
                + "  </div>\n"
                + "</template>\n\n"
                + "<script setup lang=\"ts\">\n"
                + "import { reactive, ref } from 'vue'\n"
                + "import { ElMessage, ElMessageBox } from 'element-plus'\n"
                + "import type { FormInstance, FormRules } from 'element-plus'\n"
                + "import AdvancedSearch from '@/components/common/AdvancedSearch.vue'\n"
                + "import { create" + model.className() + ", delete" + model.className() + ", query" + model.className() + "Page, update" + model.className() + " } from '@/api/modules/" + model.moduleName() + "'\n"
                + "import type { " + model.className() + "CrudRequest, " + model.className() + "View } from '@/types/" + model.moduleName() + "'\n\n"
                + "const loading = ref(false)\n"
                + "const formVisible = ref(false)\n"
                + "const detailVisible = ref(false)\n"
                + "const records = ref<" + model.className() + "View[]>([])\n"
                + "const detailItem = ref<" + model.className() + "View | null>(null)\n"
                + "const editingId = ref<" + tsScalarType(model.primaryKeyJavaType()) + " | null>(null)\n"
                + "const keyword = ref('')\n"
                + "const page = ref(1)\n"
                + "const size = ref(20)\n"
                + "const total = ref(0)\n"
                + "const formRef = ref<FormInstance>()\n\n"
                + "const form = reactive<" + model.className() + "CrudRequest>(" + renderTsInitialForm(model) + ")\n\n"
                + "const rules = reactive<FormRules>(" + renderTsRules(model) + ")\n\n"
                + "void load()\n\n"
                + "async function load() {\n"
                + "  loading.value = true\n"
                + "  try {\n"
                + "    const result = await query" + model.className() + "Page({\n"
                + "      keyword: keyword.value || undefined,\n"
                + "      page: page.value,\n"
                + "      size: size.value,\n"
                + "    })\n"
                + "    records.value = result.records\n"
                + "    total.value = result.total\n"
                + "  } finally {\n"
                + "    loading.value = false\n"
                + "  }\n"
                + "}\n\n"
                + "function handleSearch() {\n"
                + "  page.value = 1\n"
                + "  void load()\n"
                + "}\n\n"
                + "function resetSearch() {\n"
                + "  keyword.value = ''\n"
                + "  page.value = 1\n"
                + "  void load()\n"
                + "}\n\n"
                + "function handleSizeChange(value: number) {\n"
                + "  size.value = value\n"
                + "  page.value = 1\n"
                + "  void load()\n"
                + "}\n\n"
                + "function handleCurrentChange(value: number) {\n"
                + "  page.value = value\n"
                + "  void load()\n"
                + "}\n\n"
                + "function openDetail(row: " + model.className() + "View) {\n"
                + "  detailItem.value = row\n"
                + "  detailVisible.value = true\n"
                + "}\n\n"
                + "function openForm(row?: " + model.className() + "View) {\n"
                + "  editingId.value = row?." + model.primaryKeyField() + " ?? null\n"
                + "  Object.assign(form, toForm(row))\n"
                + "  formVisible.value = true\n"
                + "}\n\n"
                + "async function submit() {\n"
                + "  if (!formRef.value) {\n"
                + "    return\n"
                + "  }\n"
                + "  await formRef.value.validate()\n"
                + "  if (editingId.value === null) {\n"
                + "    await create" + model.className() + "(form)\n"
                + "    ElMessage.success('已创建')\n"
                + "  } else {\n"
                + "    await update" + model.className() + "(editingId.value, form)\n"
                + "    ElMessage.success('已更新')\n"
                + "  }\n"
                + "  formVisible.value = false\n"
                + "  await load()\n"
                + "}\n\n"
                + "async function remove(row: " + model.className() + "View) {\n"
                + "  await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除确认', { type: 'warning' })\n"
                + "  await delete" + model.className() + "(row." + model.primaryKeyField() + ")\n"
                + "  ElMessage.success('已删除')\n"
                + "  await load()\n"
                + "}\n\n"
                + "function toForm(row?: " + model.className() + "View): " + model.className() + "CrudRequest {\n"
                + renderTsToForm(model)
                + "}\n"
                + "</script>\n";
    }

    private String renderVueColumns(CodegenModel model) {
        StringBuilder builder = new StringBuilder();
        for (CodegenColumnView column : model.columns().stream().limit(8).toList()) {
            builder.append("        <el-table-column prop=\"")
                    .append(column.javaField())
                    .append("\" label=\"")
                    .append(escapeVue(columnLabel(column)))
                    .append("\" min-width=\"140\" show-overflow-tooltip />\n");
        }
        return builder.toString();
    }

    private String renderVueDescriptions(CodegenModel model) {
        StringBuilder builder = new StringBuilder();
        for (CodegenColumnView column : model.columns()) {
            builder.append("        <el-descriptions-item label=\"")
                    .append(escapeVue(columnLabel(column)))
                    .append("\">{{ detailItem.")
                    .append(column.javaField())
                    .append(" }}</el-descriptions-item>\n");
        }
        return builder.toString();
    }

    private String renderVueFormItems(CodegenModel model) {
        StringBuilder builder = new StringBuilder();
        for (CodegenColumnView column : model.editableColumns()) {
            builder.append("        <el-form-item label=\"")
                    .append(escapeVue(columnLabel(column)))
                    .append("\" prop=\"")
                    .append(column.javaField())
                    .append("\">\n")
                    .append(vueInput(column))
                    .append("        </el-form-item>\n");
        }
        return builder.toString();
    }

    private String vueInput(CodegenColumnView column) {
        if ("boolean".equals(column.tsType())) {
            return "          <el-switch v-model=\"form." + column.javaField() + "\" />\n";
        }
        if ("number".equals(column.tsType())) {
            return "          <el-input-number v-model=\"form." + column.javaField() + "\" :min=\"0\" controls-position=\"right\" style=\"width: 100%\" />\n";
        }
        if (isTemporal(column)) {
            return "          <el-date-picker v-model=\"form." + column.javaField() + "\" type=\"datetime\" value-format=\"YYYY-MM-DDTHH:mm:ss\" style=\"width: 100%\" />\n";
        }
        if (column.columnType() != null && column.columnType().toLowerCase(Locale.ROOT).contains("text")) {
            return "          <el-input v-model=\"form." + column.javaField() + "\" type=\"textarea\" :rows=\"4\" />\n";
        }
        return "          <el-input v-model=\"form." + column.javaField() + "\" />\n";
    }

    private String renderTsInitialForm(CodegenModel model) {
        if (model.editableColumns().isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{\n");
        for (CodegenColumnView column : model.editableColumns()) {
            builder.append("  ").append(column.javaField()).append(": ").append(tsDefaultValue(column)).append(",\n");
        }
        builder.append("}");
        return builder.toString();
    }

    private String renderTsRules(CodegenModel model) {
        StringBuilder builder = new StringBuilder("{\n");
        for (CodegenColumnView column : model.editableColumns()) {
            if (requestRequired(column)) {
                builder.append("  ").append(column.javaField()).append(": [{ required: true, message: '请输入")
                        .append(escapeTs(columnLabel(column)))
                        .append("', trigger: 'blur' }],\n");
            }
        }
        builder.append("}");
        return builder.toString();
    }

    private String renderTsToForm(CodegenModel model) {
        StringBuilder builder = new StringBuilder();
        builder.append("  return {\n");
        for (CodegenColumnView column : model.editableColumns()) {
            builder.append("    ").append(column.javaField()).append(": row?.").append(column.javaField()).append(" ?? ").append(tsDefaultValue(column)).append(",\n");
        }
        builder.append("  }\n");
        return builder.toString();
    }

    private String renderTenantAssignment(CodegenModel model) {
        return model.columns().stream().anyMatch(column -> isColumn(column, "tenant_id"))
                ? "        entity.setTenantId(currentTenantId());\n"
                : "";
    }

    private String renderApplyRequest(CodegenModel model) {
        StringBuilder builder = new StringBuilder();
        for (CodegenColumnView column : model.editableColumns()) {
            builder.append("        entity.set").append(upperFirst(column.javaField())).append("(request.").append(column.javaField()).append("());\n");
        }
        return builder.toString();
    }

    private String renderTenantFilter(CodegenModel model) {
        String entity = model.className() + "Entity";
        return model.columns().stream().anyMatch(column -> isColumn(column, "tenant_id"))
                ? "        query.eq(" + entity + "::getTenantId, currentTenantId());\n"
                : "";
    }

    private String renderDeletedFilter(CodegenModel model) {
        String entity = model.className() + "Entity";
        return model.columns().stream().anyMatch(column -> isColumn(column, "deleted"))
                ? "        query.eq(" + entity + "::getDeleted, 0);\n"
                : "";
    }

    private String renderKeywordFilter(CodegenModel model) {
        List<CodegenColumnView> keywordColumns = model.editableColumns().stream()
                .filter(column -> "String".equals(column.javaType()))
                .limit(3)
                .toList();
        if (keywordColumns.isEmpty()) {
            return "";
        }
        String entity = model.className() + "Entity";
        StringBuilder builder = new StringBuilder();
        builder.append("        if (StringUtils.hasText(keyword)) {\n");
        builder.append("            query.and(wrapper -> wrapper\n");
        for (int i = 0; i < keywordColumns.size(); i++) {
            CodegenColumnView column = keywordColumns.get(i);
            builder.append(i == 0 ? "                    .like(" : "                    .or().like(")
                    .append(entity)
                    .append("::get")
                    .append(upperFirst(column.javaField()))
                    .append(", keyword)");
            builder.append(i + 1 == keywordColumns.size() ? ");\n" : "\n");
        }
        builder.append("        }\n");
        return builder.toString();
    }

    private String renderDefaultOrder(CodegenModel model) {
        String entity = model.className() + "Entity";
        CodegenColumnView orderColumn = model.columns().stream()
                .filter(column -> isColumn(column, "created_at"))
                .findFirst()
                .orElseGet(() -> model.columns().stream()
                        .filter(column -> column.columnName().equals(model.primaryKeyColumn()))
                        .findFirst()
                        .orElse(model.columns().get(0)));
        return "        query.orderByDesc(" + entity + "::get" + upperFirst(orderColumn.javaField()) + ");\n";
    }

    private boolean isPrimaryKey(CodegenModel model, CodegenColumnView column) {
        return column.columnName().equals(model.primaryKeyColumn());
    }

    private String fieldFill(CodegenColumnView column) {
        String columnName = column.columnName().toLowerCase(Locale.ROOT);
        if ("created_by".equals(columnName) || "created_at".equals(columnName)) {
            return "INSERT";
        }
        if ("updated_by".equals(columnName) || "updated_at".equals(columnName)) {
            return "INSERT_UPDATE";
        }
        return null;
    }

    private boolean requestRequired(CodegenColumnView column) {
        return !column.nullable() && column.columnDefault() == null && !column.autoIncrement();
    }

    private String columnLabel(CodegenColumnView column) {
        return column.columnComment() == null || column.columnComment().isBlank() ? column.javaField() : column.columnComment();
    }

    private boolean isColumn(CodegenColumnView column, String columnName) {
        return column.columnName().equalsIgnoreCase(columnName);
    }

    private boolean isTemporal(CodegenColumnView column) {
        return switch (column.dataType().toLowerCase(Locale.ROOT)) {
            case "datetime", "timestamp", "date", "time" -> true;
            default -> false;
        };
    }

    private String tsDefaultValue(CodegenColumnView column) {
        if ("boolean".equals(column.tsType())) {
            return "false";
        }
        if ("number".equals(column.tsType())) {
            return "0";
        }
        return "''";
    }

    private String tsScalarType(String javaType) {
        return switch (javaType) {
            case "Long", "Integer", "Double", "java.math.BigDecimal" -> "number";
            case "Boolean" -> "boolean";
            default -> "string";
        };
    }

    private String upperFirst(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String escapeJava(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeVue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String escapeTs(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private CodegenTableView tableView(ResultSet rs) throws SQLException {
        return new CodegenTableView(
                rs.getString("table_name"),
                rs.getString("table_comment"),
                rs.getString("engine"),
                nullableLong(rs, "table_rows"),
                nullableLong(rs, "data_length"),
                nullableLong(rs, "index_length"),
                nullableInstantMillis(rs, "create_time"),
                nullableInstantMillis(rs, "update_time")
        );
    }

    private CodegenColumnView columnView(ResultSet rs) throws SQLException {
        String columnName = rs.getString("column_name");
        String dataType = rs.getString("data_type");
        boolean primaryKey = "PRI".equalsIgnoreCase(rs.getString("column_key"));
        boolean autoIncrement = rs.getString("extra") != null && rs.getString("extra").toLowerCase(Locale.ROOT).contains("auto_increment");
        return new CodegenColumnView(
                columnName,
                dataType,
                rs.getString("column_type"),
                "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                primaryKey,
                autoIncrement,
                rs.getString("column_default"),
                rs.getString("column_comment"),
                javaType(dataType),
                toCamel(columnName, false),
                tsType(dataType)
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Long nullableInstantMillis(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        if (timestamp == null) {
            return null;
        }
        Instant instant = timestamp.toInstant();
        return instant.toEpochMilli();
    }

    private String backendPath(CodegenModel model, String layer, String fileName) {
        return "backend/src/main/java/" + model.packageName().replace('.', '/') + "/modules/" + model.moduleName() + "/" + layer + "/" + fileName;
    }

    private Path safeTarget(String relativePath) {
        Path target = outputRoot.resolve(relativePath).normalize();
        if (!target.startsWith(outputRoot)) {
            throw new BusinessException("VALIDATION_ERROR", "生成路径越界");
        }
        return target;
    }

    private String normalizeTableName(String tableName) {
        String normalized = tableName == null ? "" : tableName.trim();
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new BusinessException("VALIDATION_ERROR", "表名格式不合法");
        }
        return normalized;
    }

    private String normalizeIdentifier(String value, String field, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new BusinessException("VALIDATION_ERROR", field + " 格式不合法");
        }
        return Character.toLowerCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String normalizeClassName(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new BusinessException("VALIDATION_ERROR", "className 格式不合法");
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String normalizePackage(String value) {
        String normalized = value == null || value.isBlank() ? DEFAULT_PACKAGE : value.trim();
        if (!normalized.matches("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$")) {
            throw new BusinessException("VALIDATION_ERROR", "packageName 格式不合法");
        }
        return normalized;
    }

    private String stripPrefix(String tableName) {
        String value = tableName;
        for (String prefix : List.of("sys_", "wf_")) {
            if (value.startsWith(prefix)) {
                return value.substring(prefix.length());
            }
        }
        return value;
    }

    private String toCamel(String value, boolean upperFirst) {
        StringBuilder builder = new StringBuilder();
        for (String part : value.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        if (builder.isEmpty()) {
            return upperFirst ? "Generated" : "generated";
        }
        if (!upperFirst) {
            builder.setCharAt(0, Character.toLowerCase(builder.charAt(0)));
        }
        return builder.toString();
    }

    private String toKebab(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                builder.append('-');
            }
            builder.append(Character.toLowerCase(ch));
        }
        return builder.toString();
    }

    private String javaType(String dataType) {
        return switch (dataType.toLowerCase(Locale.ROOT)) {
            case "bigint" -> "Long";
            case "int", "integer", "smallint", "tinyint", "mediumint" -> "Integer";
            case "decimal", "numeric" -> "java.math.BigDecimal";
            case "float", "double" -> "Double";
            case "datetime", "timestamp", "date", "time" -> "LocalDateTime";
            case "bit", "boolean" -> "Boolean";
            default -> "String";
        };
    }

    private String tsType(String dataType) {
        return switch (dataType.toLowerCase(Locale.ROOT)) {
            case "bigint", "int", "integer", "smallint", "tinyint", "mediumint", "decimal", "numeric", "float", "double" -> "number";
            case "bit", "boolean" -> "boolean";
            default -> "string";
        };
    }
}