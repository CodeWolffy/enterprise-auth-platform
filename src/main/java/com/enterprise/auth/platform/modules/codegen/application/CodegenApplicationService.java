package com.enterprise.auth.platform.modules.codegen.application;

import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.codegen.domain.CodegenNaming;
import com.enterprise.auth.platform.modules.codegen.domain.CodegenTypeMappings;
import com.enterprise.auth.platform.modules.codegen.domain.model.ColumnDefinition;
import com.enterprise.auth.platform.modules.codegen.domain.model.GeneratedFile;
import com.enterprise.auth.platform.modules.codegen.domain.model.RenderContext;
import com.enterprise.auth.platform.modules.codegen.domain.model.TableDefinition;
import com.enterprise.auth.platform.modules.codegen.domain.render.CodeRenderer;
import com.enterprise.auth.platform.modules.codegen.infrastructure.CodegenFileWriter;
import com.enterprise.auth.platform.modules.codegen.infrastructure.CodegenZipBuilder;
import com.enterprise.auth.platform.modules.codegen.infrastructure.TableMetadataExtractor;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 代码生成应用服务：参数校验与流程编排（元数据抽取 → 模板渲染 → 过滤 → 落盘/打包 → 资源注册）。
 */
@Service
public class CodegenApplicationService {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    private static final Set<String> SYSTEM_COLUMNS = Set.of("id", "tenant_id", "created_by", "updated_by", "deleted", "created_at", "updated_at");
    private static final String DEFAULT_PACKAGE = "com.enterprise.auth.platform.generated";
    private static final String SERVER_MANAGED_OUTPUT_ROOT = "SERVER_MANAGED";

    private final TableMetadataExtractor metadataExtractor;
    private final CodegenFileWriter fileWriter;
    private final CodegenZipBuilder zipBuilder;
    private final CodeRenderer codeRenderer = new CodeRenderer();
    private final CodegenResourceRegistrationService registrationService;
    private final CodegenMetadataService metadataService;

    @Lazy
    @Autowired
    private CodegenApplicationService self;

    public CodegenApplicationService(
            TableMetadataExtractor metadataExtractor,
            CodegenFileWriter fileWriter,
            CodegenZipBuilder zipBuilder,
            CodegenResourceRegistrationService registrationService,
            CodegenMetadataService metadataService
    ) {
        this.metadataExtractor = metadataExtractor;
        this.fileWriter = fileWriter;
        this.zipBuilder = zipBuilder;
        this.registrationService = registrationService;
        this.metadataService = metadataService;
    }

    @Transactional(readOnly = true)
    public PageResult<CodegenTableView> tables(String keyword, int page, int size) {
        PageResult<TableDefinition> result = metadataExtractor.pageImportedSourceTables(currentTenantId(), keyword, page, size);
        return PageResult.of(result.total(), result.page(), result.size(), result.records().stream().map(this::toTableView).toList());
    }

    @Transactional(readOnly = true)
    public CodegenTableDetailView table(String tableName) {
        return new CodegenTableDetailView(
                toTableView(requireTable(tableName)),
                columns(tableName).stream().map(this::toColumnView).toList()
        );
    }

    @Transactional(readOnly = true)
    public CodegenPreviewResult preview(CodegenCommand command) {
        RenderContext model = buildModel(command);
        return new CodegenPreviewResult(
                model.tableName(),
                model.moduleName(),
                model.className(),
                SERVER_MANAGED_OUTPUT_ROOT,
                codeRenderer.renderFiles(model, command.includeBackend(), command.includeFrontend()).stream()
                        .map(file -> new CodegenFilePreview(file.path(), file.language(), file.content()))
                        .toList(),
                command.selectedFiles(),
                command.autoRegister()
        );
    }

    @Transactional
    public CodegenGenerateResult generate(CodegenCommand command) {
        CodegenPreviewResult preview = self.preview(command);
        List<String> selectedFiles = resolveSelectedFiles(command.selectedFiles(), preview.files(), "至少选择一个生成文件");
        List<String> written = fileWriter.write(selectFiles(preview.files(), selectedFiles), command.overwrite());
        List<String> registered = command.autoRegister()
                ? registrationService.register(preview.tableName(), preview.moduleName(), preview.className())
                : List.of();
        return new CodegenGenerateResult(preview.tableName(), preview.moduleName(), SERVER_MANAGED_OUTPUT_ROOT, written, registered);
    }

    @Transactional(readOnly = true)
    public CodegenArtifactDownload download(CodegenCommand command) {
        CodegenPreviewResult preview = self.preview(command);
        List<String> selectedFiles = resolveSelectedFiles(command.selectedFiles(), preview.files(), "至少选择一个导出文件");
        return new CodegenArtifactDownload(
                preview.moduleName() + "-" + preview.className() + ".zip",
                "application/zip",
                zipBuilder.build(selectFiles(preview.files(), selectedFiles))
        );
    }

    private List<GeneratedFile> selectFiles(List<CodegenFilePreview> files, List<String> selectedFiles) {
        return files.stream()
                .filter(file -> selectedFiles.contains(file.path()))
                .map(file -> new GeneratedFile(file.path(), file.language(), file.content()))
                .toList();
    }

    private List<String> resolveSelectedFiles(List<String> selectedFiles, List<CodegenFilePreview> files, String emptyMessage) {
        if (selectedFiles == null) {
            return files.stream().map(CodegenFilePreview::path).toList();
        }
        Set<String> allowedPaths = files.stream()
                .map(CodegenFilePreview::path)
                .collect(Collectors.toSet());
        List<String> resolved = selectedFiles.stream()
                .filter(allowedPaths::contains)
                .distinct()
                .toList();
        if (resolved.size() != selectedFiles.stream().distinct().count()) {
            throw new BusinessException("VALIDATION_ERROR", "选择的生成文件不存在或不属于本次生成结果");
        }
        if (resolved.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", emptyMessage);
        }
        return resolved;
    }

    private RenderContext buildModel(CodegenCommand command) {
        if (command == null) {
            throw new BusinessException("VALIDATION_ERROR", "生成参数不能为空");
        }
        TableDefinition table = requireTable(command.tableName());
        List<ColumnDefinition> columns = columns(table.tableName());
        if (columns.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "数据表没有可生成字段");
        }
        String moduleName = normalizeIdentifier(command.moduleName(), "moduleName", CodegenNaming.toCamel(table.tableName(), false));
        String className = normalizeClassName(command.className(), CodegenNaming.toCamel(CodegenNaming.stripPrefix(table.tableName()), true));
        String packageName = normalizePackage(command.packageName());
        ColumnDefinition primaryKey = columns.stream().filter(ColumnDefinition::primaryKey).findFirst().orElse(columns.get(0));
        List<ColumnDefinition> editableColumns = columns.stream()
                .filter(column -> !column.columnName().equals(primaryKey.columnName()))
                .filter(column -> !SYSTEM_COLUMNS.contains(column.columnName().toLowerCase(Locale.ROOT)))
                .filter(column -> column.insert() || column.edit())
                .toList();
        if (editableColumns.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "数据表没有可写业务字段，无法生成完整 CRUD");
        }
        List<ColumnDefinition> insertColumns = editableColumns.stream()
                .filter(ColumnDefinition::insert)
                .toList();
        List<ColumnDefinition> editColumns = editableColumns.stream()
                .filter(ColumnDefinition::edit)
                .toList();
        List<ColumnDefinition> listColumns = columns.stream()
                .filter(ColumnDefinition::list)
                .limit(8)
                .toList();
        List<ColumnDefinition> queryColumns = columns.stream()
                .filter(ColumnDefinition::query)
                .toList();
        boolean includeBackend = command.includeBackend();
        boolean includeFrontend = command.includeFrontend();
        if (!includeBackend && !includeFrontend) {
            throw new BusinessException("VALIDATION_ERROR", "至少选择一种生成范围");
        }
        return new RenderContext(
                table.tableName(),
                moduleName,
                packageName,
                className,
                Character.toLowerCase(className.charAt(0)) + className.substring(1),
                CodegenNaming.toKebab(className),
                table.tableComment() == null || table.tableComment().isBlank() ? className : table.tableComment(),
                primaryKey.columnName(),
                primaryKey.javaField(),
                primaryKey.javaType(),
                columns,
                editableColumns,
                insertColumns,
                editColumns,
                listColumns,
                queryColumns
        );
    }

    private TableDefinition requireTable(String tableName) {
        String safeTableName = normalizeTableName(tableName);
        ensureImportedTable(safeTableName);
        return metadataExtractor.findTable(safeTableName)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "数据表不存在：" + safeTableName));
    }

    private List<ColumnDefinition> columns(String tableName) {
        String safeTableName = normalizeTableName(tableName);
        List<ColumnDefinition> rawColumns = metadataExtractor.readColumnsForGeneration(safeTableName);
        Map<String, ColumnDefinition> overrides = metadataService.importedColumnOverrides(safeTableName);
        if (overrides.isEmpty()) {
            return rawColumns;
        }
        return rawColumns.stream()
                .map(column -> mergeColumnOverride(column, overrides.get(column.columnName())))
                .toList();
    }

    private ColumnDefinition mergeColumnOverride(ColumnDefinition rawColumn, ColumnDefinition override) {
        if (override == null) {
            return rawColumn;
        }
        return new ColumnDefinition(
                rawColumn.columnName(),
                rawColumn.dataType(),
                rawColumn.columnType(),
                rawColumn.nullable(),
                rawColumn.primaryKey(),
                rawColumn.autoIncrement(),
                override.required(),
                rawColumn.columnDefault(),
                override.columnComment(),
                override.javaType(),
                override.javaField(),
                CodegenTypeMappings.generationTsTypeFromJava(override.javaType(), rawColumn.dataType()),
                override.insert(),
                override.edit(),
                override.list(),
                override.query(),
                override.queryType(),
                override.htmlType(),
                override.dictType()
        );
    }

    private void ensureImportedTable(String tableName) {
        if (!metadataExtractor.isTableImported(currentTenantId(), tableName)) {
            throw new BusinessException("ACCESS_DENIED", "数据表未导入代码生成配置");
        }
    }

    private String currentTenantId() {
        return TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID);
    }

    private CodegenTableView toTableView(TableDefinition table) {
        return new CodegenTableView(
                table.tableName(),
                table.tableComment(),
                table.engine(),
                table.tableRows(),
                table.dataLength(),
                table.indexLength(),
                table.createdAt(),
                table.updatedAt()
        );
    }

    private CodegenColumnView toColumnView(ColumnDefinition column) {
        return new CodegenColumnView(
                column.columnName(),
                column.dataType(),
                column.columnType(),
                column.nullable(),
                column.primaryKey(),
                column.autoIncrement(),
                column.required(),
                column.columnDefault(),
                column.columnComment(),
                column.javaType(),
                column.javaField(),
                column.tsType(),
                column.insert(),
                column.edit(),
                column.list(),
                column.query(),
                column.queryType(),
                column.htmlType(),
                column.dictType()
        );
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
}
