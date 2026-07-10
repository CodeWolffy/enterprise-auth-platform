package com.enterprise.auth.platform.modules.codegen.application;

import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataDtos.ColumnConfigView;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataDtos.ConnectionTestResult;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataDtos.DataSourceAuthorizationRequest;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataDtos.DataSourceRequest;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataDtos.DataSourceView;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataDtos.ImportTableRequest;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataDtos.ImportedTableView;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataDtos.TableConfigDetailView;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataDtos.UpdateColumnsRequest;
import com.enterprise.auth.platform.modules.codegen.domain.CodegenNaming;
import com.enterprise.auth.platform.modules.codegen.domain.CodegenTypeMappings;
import com.enterprise.auth.platform.modules.codegen.domain.model.ColumnDefinition;
import com.enterprise.auth.platform.modules.codegen.domain.model.TableDefinition;
import com.enterprise.auth.platform.modules.codegen.infrastructure.CodegenMetadataRepository;
import com.enterprise.auth.platform.modules.codegen.infrastructure.TableMetadataExtractor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 代码生成元数据应用服务：数据源与导入表配置的校验、租户与业务默认值编排。
 */
@Service
public class CodegenMetadataService {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    private static final String LOCAL_JDBC_URL = "LOCAL";
    private static final String DEFAULT_PACKAGE = "com.enterprise.auth.platform.generated";
    private static final Set<String> SYSTEM_COLUMNS = Set.of("id", "tenant_id", "created_by", "updated_by", "deleted", "created_at", "updated_at");

    private final CodegenMetadataRepository repository;
    private final TableMetadataExtractor metadataExtractor;

    public CodegenMetadataService(CodegenMetadataRepository repository, TableMetadataExtractor metadataExtractor) {
        this.repository = repository;
        this.metadataExtractor = metadataExtractor;
    }

    @Transactional
    public List<DataSourceView> dataSources() {
        ensureLocalDataSource();
        return repository.findDataSources(TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID));
    }

    @Transactional
    public DataSourceView createDataSource(DataSourceRequest request) {
        validateDataSourceRequest(request);
        Long id = repository.insertDataSource(
                TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID),
                request.name().trim(),
                request.jdbcUrl().trim(),
                trimToNull(request.username()),
                maskPassword(request.password()),
                trimToNull(request.dbName()),
                trimToNull(request.host()),
                request.port(),
                Boolean.FALSE.equals(request.enabled()) ? 0 : 1,
                isLocalJdbcUrl(request.jdbcUrl()) ? 1 : 0,
                isLocalJdbcUrl(request.jdbcUrl()) ? "当前应用库默认授权" : "外部数据源待显式授权");
        return dataSource(id);
    }

    @Transactional
    public DataSourceView updateDataSource(Long id, DataSourceRequest request) {
        validateDataSourceRequest(request);
        DataSourceView existing = requireDataSource(id);
        boolean resetAuthorization = existing.external() && !existing.jdbcUrl().equals(request.jdbcUrl().trim());
        repository.updateDataSource(
                TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID),
                id,
                request.name().trim(),
                request.jdbcUrl().trim(),
                trimToNull(request.username()),
                StringUtils.hasText(request.password()) ? maskPassword(request.password()) : null,
                trimToNull(request.dbName()),
                trimToNull(request.host()),
                request.port(),
                Boolean.FALSE.equals(request.enabled()) ? 0 : 1,
                isLocalJdbcUrl(request.jdbcUrl()) ? 1 : resetAuthorization ? 0 : existing.externalAuthorized() ? 1 : 0,
                isLocalJdbcUrl(request.jdbcUrl()) ? java.sql.Timestamp.from(Instant.now()) : resetAuthorization ? null : timestampFromInstant(existing.authorizedAt()),
                isLocalJdbcUrl(request.jdbcUrl()) ? "当前应用库默认授权" : resetAuthorization ? "外部数据源地址已变更，需重新显式授权" : existing.authorizationNote());
        return dataSource(id);
    }

    @Transactional
    public void deleteDataSource(Long id) {
        DataSourceView dataSource = requireDataSource(id);
        if (LOCAL_JDBC_URL.equalsIgnoreCase(dataSource.jdbcUrl())) {
            throw new BusinessException("VALIDATION_ERROR", "当前应用库数据源不允许删除");
        }
        if (repository.countImportedTablesByDataSource(
                TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID), id) > 0) {
            throw new BusinessException("VALIDATION_ERROR", "数据源下已有导入表配置，不能删除");
        }
        repository.softDeleteDataSource(
                TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID), id);
    }

    @Transactional
    public DataSourceView authorizeDataSource(Long id, DataSourceAuthorizationRequest request) {
        DataSourceView dataSource = requireDataSource(id);
        if (!dataSource.external()) {
            throw new BusinessException("VALIDATION_ERROR", "当前应用库无需外部授权");
        }
        String note = trimToNull(request == null ? null : request.note());
        repository.markDataSourceAuthorized(
                TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID),
                id,
                note == null ? "已确认该外部数据源属于当前授权范围" : note);
        return dataSource(id);
    }

    @Transactional(readOnly = true)
    public ConnectionTestResult testConnection(Long id) {
        DataSourceView dataSource = requireDataSource(id);
        if (LOCAL_JDBC_URL.equalsIgnoreCase(dataSource.jdbcUrl())) {
            Integer value = repository.ping();
            return new ConnectionTestResult(id, value != null && value == 1, "当前应用库连接正常");
        }
        if (!dataSource.externalAuthorized()) {
            return new ConnectionTestResult(id, false, "外部数据源尚未显式授权，请先确认授权范围");
        }
        return new ConnectionTestResult(id, false, "外部数据源已授权，连接执行器尚未启用");
    }

    @Transactional(readOnly = true)
    public PageResult<CodegenTableView> dataSourceTables(Long dataSourceId, String keyword, int page, int size) {
        DataSourceView dataSource = requireDataSource(dataSourceId);
        requireLocalDataSource(dataSource);
        PageResult<TableDefinition> result = metadataExtractor.pageSourceTables(keyword, page, size);
        return PageResult.of(result.total(), result.page(), result.size(), result.records().stream().map(this::toTableView).toList());
    }

    @Transactional
    public List<ImportedTableView> importTables(ImportTableRequest request) {
        if (request == null || request.tableNames() == null || request.tableNames().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "至少选择一张表");
        }
        DataSourceView dataSource = requireDataSource(request.dataSourceId());
        requireLocalDataSource(dataSource);
        List<ImportedTableView> imported = new ArrayList<>();
        for (String tableName : request.tableNames()) {
            String safeTableName = normalizeTableName(tableName);
            TableDefinition table = requireSourceTable(safeTableName);
            Long tableId = upsertTableConfig(dataSource.id(), table, request.packageName(), request.author());
            upsertColumnConfigs(tableId, safeTableName);
            imported.add(tableConfig(tableId).table());
        }
        return imported;
    }

    @Transactional(readOnly = true)
    public PageResult<ImportedTableView> importedTables(String keyword, int page, int size) {
        return repository.pageImportedTables(
                TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID),
                keyword,
                page,
                size
        );
    }

    @Transactional(readOnly = true)
    public TableConfigDetailView tableConfig(Long tableId) {
        ImportedTableView table = repository.findImportedTable(
                        TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID), tableId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "表配置不存在"));
        List<ColumnConfigView> columns = repository.findColumnConfigs(
                TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID), tableId);
        return new TableConfigDetailView(table, columns);
    }

    @Transactional
    public TableConfigDetailView updateColumns(Long tableId, UpdateColumnsRequest request) {
        tableConfig(tableId);
        if (request == null || request.columns() == null || request.columns().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "字段配置不能为空");
        }
        for (ColumnConfigView column : request.columns()) {
            if (!StringUtils.hasText(column.columnName())) {
                continue;
            }
            repository.updateColumnConfig(
                    TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID),
                    tableId,
                    column
            );
        }
        return tableConfig(tableId);
    }

    @Transactional
    public void deleteImportedTable(Long tableId) {
        if (tableId == null) {
            throw new BusinessException("VALIDATION_ERROR", "表配置 ID 不能为空");
        }
        int updated = repository.softDeleteImportedTable(
                TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID), tableId);
        if (updated == 0) {
            throw new BusinessException("NOT_FOUND", "表配置不存在");
        }
    }

    /**
     * 已导入表的字段级配置覆盖，按 sort 顺序返回，供生成链路合并使用。
     */
    public Map<String, ColumnDefinition> importedColumnOverrides(String tableName) {
        String tenantId = TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID);
        Long tableId = repository.findLatestImportedTableId(tenantId, tableName).orElse(null);
        if (tableId == null) {
            return Map.of();
        }
        Map<String, ColumnDefinition> overrides = new LinkedHashMap<>();
        for (ColumnConfigView config : repository.findColumnConfigs(tenantId, tableId)) {
            overrides.put(config.columnName(), new ColumnDefinition(
                    config.columnName(),
                    config.dataType(),
                    config.columnType(),
                    !config.required(),
                    config.primaryKey(),
                    false,
                    config.required(),
                    null,
                    config.columnComment(),
                    StringUtils.hasText(config.javaType()) ? config.javaType() : CodegenTypeMappings.importJavaType(config.dataType()),
                    StringUtils.hasText(config.javaField()) ? config.javaField() : CodegenNaming.toCamel(config.columnName(), false),
                    CodegenTypeMappings.importTsType(StringUtils.hasText(config.dataType()) ? config.dataType() : "varchar"),
                    config.insert(),
                    config.edit(),
                    config.list(),
                    config.query(),
                    config.queryType(),
                    config.htmlType(),
                    config.dictType()
            ));
        }
        return overrides;
    }

    private Long upsertTableConfig(Long dataSourceId, TableDefinition table, String packageName, String author) {
        String className = CodegenNaming.toCamel(CodegenNaming.stripPrefix(table.tableName()), true);
        String moduleName = CodegenNaming.toCamel(CodegenNaming.stripPrefix(table.tableName()), false);
        return repository.upsertTableConfig(
                TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID),
                dataSourceId,
                table.tableName(),
                table.tableComment(),
                className,
                StringUtils.hasText(packageName) ? packageName.trim() : DEFAULT_PACKAGE,
                moduleName,
                table.tableName(),
                StringUtils.hasText(table.tableComment()) ? table.tableComment() : className,
                trimToNull(author));
    }

    private void upsertColumnConfigs(Long tableId, String tableName) {
        List<ColumnDefinition> columns = metadataExtractor.readColumnsForImport(tableName);
        int sort = 0;
        for (ColumnDefinition column : columns) {
            boolean systemColumn = SYSTEM_COLUMNS.contains(column.columnName().toLowerCase(Locale.ROOT));
            boolean businessColumn = !systemColumn && !column.primaryKey();
            repository.upsertColumnConfig(
                TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID),
                    tableId,
                    column.columnName(),
                    column.columnComment(),
                    column.columnType(),
                    column.dataType(),
                    column.javaType(),
                    column.javaField(),
                    column.primaryKey() ? 1 : 0,
                    column.required() ? 1 : 0,
                    businessColumn ? 1 : 0,
                    businessColumn ? 1 : 0,
                    !systemColumn ? 1 : 0,
                    defaultQueryColumn(column) ? 1 : 0,
                    defaultQueryType(column),
                    defaultHtmlType(column),
                    null,
                    sort++);
        }
    }

    private boolean defaultQueryColumn(ColumnDefinition column) {
        if (SYSTEM_COLUMNS.contains(column.columnName().toLowerCase(Locale.ROOT)) || column.primaryKey()) {
            return false;
        }
        return "String".equals(column.javaType()) || isTemporal(column.dataType());
    }

    private String defaultQueryType(ColumnDefinition column) {
        if ("String".equals(column.javaType())) {
            return "LIKE";
        }
        if (isTemporal(column.dataType())) {
            return "BETWEEN";
        }
        return "EQ";
    }

    private String defaultHtmlType(ColumnDefinition column) {
        String dataType = column.dataType().toLowerCase(Locale.ROOT);
        if (dataType.contains("text")) {
            return "textarea";
        }
        if (dataType.contains("date") || dataType.contains("time")) {
            return "datetime";
        }
        if (Set.of("int", "bigint", "decimal", "double", "float").contains(dataType)) {
            return "number";
        }
        return "input";
    }

    private boolean isTemporal(String dataType) {
        String normalized = dataType == null ? "" : dataType.toLowerCase(Locale.ROOT);
        return normalized.contains("date") || normalized.contains("time");
    }

    private TableDefinition requireSourceTable(String tableName) {
        return metadataExtractor.findTable(tableName)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "数据表不存在：" + tableName));
    }

    private DataSourceView dataSource(Long id) {
        return repository.findDataSource(
                        TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID), id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "数据源不存在"));
    }

    private DataSourceView requireDataSource(Long id) {
        if (id == null) {
            List<DataSourceView> dataSources = dataSources();
            return dataSources.stream().findFirst().orElseThrow(() -> new BusinessException("NOT_FOUND", "数据源不存在"));
        }
        return dataSource(id);
    }

    private void requireLocalDataSource(DataSourceView dataSource) {
        if (!LOCAL_JDBC_URL.equalsIgnoreCase(dataSource.jdbcUrl())) {
            throw new BusinessException("VALIDATION_ERROR", "外部数据源读取尚未启用");
        }
    }

    private void ensureLocalDataSource() {
        String tenantId = TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID);
        if (repository.countLocalDataSources(tenantId) > 0) {
            return;
        }
        repository.insertLocalDataSource(tenantId);
    }

    private void validateDataSourceRequest(DataSourceRequest request) {
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new BusinessException("VALIDATION_ERROR", "数据源名称不能为空");
        }
        if (!StringUtils.hasText(request.jdbcUrl())) {
            throw new BusinessException("VALIDATION_ERROR", "JDBC 地址不能为空");
        }
    }

    private String normalizeTableName(String tableName) {
        String normalized = tableName == null ? "" : tableName.trim();
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new BusinessException("VALIDATION_ERROR", "表名格式不合法");
        }
        return normalized;
    }

    private String maskPassword(String password) {
        return StringUtils.hasText(password) ? "{managed}" : null;
    }

    private boolean isLocalJdbcUrl(String jdbcUrl) {
        return LOCAL_JDBC_URL.equalsIgnoreCase(jdbcUrl == null ? null : jdbcUrl.trim());
    }

    private java.sql.Timestamp timestampFromInstant(Instant instant) {
        return instant == null ? null : java.sql.Timestamp.from(instant);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
}
