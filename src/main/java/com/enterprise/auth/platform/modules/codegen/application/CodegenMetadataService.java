package com.enterprise.auth.platform.modules.codegen.application;

import com.enterprise.auth.platform.common.context.TenantContext;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CodegenMetadataService {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    private static final String LOCAL_JDBC_URL = "LOCAL";
    private static final String DEFAULT_PACKAGE = "com.enterprise.auth.platform.generated";
    private static final Set<String> SYSTEM_COLUMNS = Set.of("id", "tenant_id", "created_by", "updated_by", "deleted", "created_at", "updated_at");

    private final JdbcTemplate jdbcTemplate;

    public CodegenMetadataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<DataSourceView> dataSources() {
        ensureLocalDataSource();
        return jdbcTemplate.query("""
                        SELECT id, name, jdbc_url, username, db_name, host, port, enabled, external_authorized, authorized_at, authorization_note, created_at, updated_at
                        FROM codegen_data_source
                        WHERE tenant_id = ? AND deleted = 0
                        ORDER BY id
                        """,
                (rs, rowNum) -> dataSourceView(rs),
                currentTenantId());
    }

    @Transactional
    public DataSourceView createDataSource(DataSourceRequest request) {
        validateDataSourceRequest(request);
        jdbcTemplate.update("""
                        INSERT INTO codegen_data_source(tenant_id, name, jdbc_url, username, password_cipher, db_name, host, port, enabled, external_authorized, authorization_note, created_by, updated_by, deleted)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?,'system','system',0)
                        """,
                currentTenantId(),
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
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return dataSource(id);
    }

    @Transactional
    public DataSourceView updateDataSource(Long id, DataSourceRequest request) {
        validateDataSourceRequest(request);
        DataSourceView existing = requireDataSource(id);
        boolean resetAuthorization = existing.external() && !existing.jdbcUrl().equals(request.jdbcUrl().trim());
        jdbcTemplate.update("""
                        UPDATE codegen_data_source
                        SET name = ?, jdbc_url = ?, username = ?, password_cipher = COALESCE(?, password_cipher), db_name = ?, host = ?, port = ?, enabled = ?, external_authorized = ?, authorized_at = ?, authorization_note = ?, updated_by = 'system'
                        WHERE tenant_id = ? AND id = ? AND deleted = 0
                        """,
                request.name().trim(),
                request.jdbcUrl().trim(),
                trimToNull(request.username()),
                StringUtils.hasText(request.password()) ? maskPassword(request.password()) : null,
                trimToNull(request.dbName()),
                trimToNull(request.host()),
                request.port(),
                Boolean.FALSE.equals(request.enabled()) ? 0 : 1,
                isLocalJdbcUrl(request.jdbcUrl()) ? 1 : resetAuthorization ? 0 : existing.externalAuthorized() ? 1 : 0,
                isLocalJdbcUrl(request.jdbcUrl()) ? java.sql.Timestamp.from(Instant.now()) : resetAuthorization ? null : timestampFromMillis(existing.authorizedAt()),
                isLocalJdbcUrl(request.jdbcUrl()) ? "当前应用库默认授权" : resetAuthorization ? "外部数据源地址已变更，需重新显式授权" : existing.authorizationNote(),
                currentTenantId(),
                id);
        return dataSource(id);
    }

    @Transactional
    public void deleteDataSource(Long id) {
        DataSourceView dataSource = requireDataSource(id);
        if (LOCAL_JDBC_URL.equalsIgnoreCase(dataSource.jdbcUrl())) {
            throw new BusinessException("VALIDATION_ERROR", "当前应用库数据源不允许删除");
        }
        Long importedTables = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM codegen_table
                        WHERE tenant_id = ? AND data_source_id = ? AND deleted = 0
                        """,
                Long.class,
                currentTenantId(),
                id);
        if (importedTables != null && importedTables > 0) {
            throw new BusinessException("VALIDATION_ERROR", "数据源下已有导入表配置，不能删除");
        }
        jdbcTemplate.update("UPDATE codegen_data_source SET deleted = 1, updated_by = 'system' WHERE tenant_id = ? AND id = ?", currentTenantId(), id);
    }

    @Transactional
    public DataSourceView authorizeDataSource(Long id, DataSourceAuthorizationRequest request) {
        DataSourceView dataSource = requireDataSource(id);
        if (!dataSource.external()) {
            throw new BusinessException("VALIDATION_ERROR", "当前应用库无需外部授权");
        }
        String note = trimToNull(request == null ? null : request.note());
        jdbcTemplate.update("""
                        UPDATE codegen_data_source
                        SET external_authorized = 1, authorized_at = CURRENT_TIMESTAMP, authorization_note = ?, updated_by = 'system'
                        WHERE tenant_id = ? AND id = ? AND deleted = 0
                        """,
                note == null ? "已确认该外部数据源属于当前授权范围" : note,
                currentTenantId(),
                id);
        return dataSource(id);
    }

    @Transactional(readOnly = true)
    public ConnectionTestResult testConnection(Long id) {
        DataSourceView dataSource = requireDataSource(id);
        if (LOCAL_JDBC_URL.equalsIgnoreCase(dataSource.jdbcUrl())) {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
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
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<Object> params = new ArrayList<>();
        String filterSql = " WHERE table_schema = DATABASE()"
                + " AND table_name IN (SELECT table_name FROM sys_codegen_allowlist WHERE tenant_id = ? AND enabled = 1 AND deleted = 0)";
        params.add(currentTenantId());
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
            CodegenTableView table = requireAllowlistedTable(safeTableName);
            Long tableId = upsertTableConfig(dataSource.id(), table, request.packageName(), request.author());
            upsertColumnConfigs(tableId, safeTableName);
            imported.add(tableConfig(tableId).table());
        }
        return imported;
    }

    @Transactional(readOnly = true)
    public PageResult<ImportedTableView> importedTables(String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<Object> params = new ArrayList<>();
        String filterSql = " WHERE t.tenant_id = ? AND t.deleted = 0";
        params.add(currentTenantId());
        if (!normalizedKeyword.isBlank()) {
            filterSql += " AND (t.table_name LIKE ? OR t.table_comment LIKE ? OR t.class_name LIKE ?)";
            String like = "%" + normalizedKeyword + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM codegen_table t" + filterSql, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add((safePage - 1) * safeSize);
        pageParams.add(safeSize);
        List<ImportedTableView> records = jdbcTemplate.query("""
                        SELECT t.*, (SELECT COUNT(*) FROM codegen_table_column c WHERE c.tenant_id = t.tenant_id AND c.table_id = t.id) AS column_count
                        FROM codegen_table t
                        """ + filterSql + " ORDER BY t.updated_at DESC LIMIT ?, ?",
                (rs, rowNum) -> importedTableView(rs),
                pageParams.toArray());
        return PageResult.of(total == null ? 0 : total, safePage, safeSize, records);
    }

    @Transactional(readOnly = true)
    public TableConfigDetailView tableConfig(Long tableId) {
        ImportedTableView table = jdbcTemplate.query("""
                        SELECT t.*, (SELECT COUNT(*) FROM codegen_table_column c WHERE c.tenant_id = t.tenant_id AND c.table_id = t.id) AS column_count
                        FROM codegen_table t
                        WHERE t.tenant_id = ? AND t.id = ? AND t.deleted = 0
                        """,
                (rs, rowNum) -> importedTableView(rs),
                currentTenantId(),
                tableId).stream().findFirst().orElseThrow(() -> new BusinessException("NOT_FOUND", "表配置不存在"));
        List<ColumnConfigView> columns = jdbcTemplate.query("""
                        SELECT * FROM codegen_table_column
                        WHERE tenant_id = ? AND table_id = ?
                        ORDER BY sort, id
                        """,
                (rs, rowNum) -> columnConfigView(rs),
                currentTenantId(),
                tableId);
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
            jdbcTemplate.update("""
                            UPDATE codegen_table_column
                            SET column_comment = ?, java_type = ?, java_field = ?, is_required = ?, is_insert = ?, is_edit = ?, is_list = ?, is_query = ?, query_type = ?, html_type = ?, dict_type = ?, sort = ?, updated_by = 'system'
                            WHERE tenant_id = ? AND table_id = ? AND column_name = ?
                            """,
                    trimToNull(column.columnComment()),
                    trimToNull(column.javaType()),
                    trimToNull(column.javaField()),
                    column.required() ? 1 : 0,
                    column.insert() ? 1 : 0,
                    column.edit() ? 1 : 0,
                    column.list() ? 1 : 0,
                    column.query() ? 1 : 0,
                    StringUtils.hasText(column.queryType()) ? column.queryType().trim() : "EQ",
                    StringUtils.hasText(column.htmlType()) ? column.htmlType().trim() : "input",
                    trimToNull(column.dictType()),
                    column.sort() == null ? 0 : column.sort(),
                    currentTenantId(),
                    tableId,
                    column.columnName());
        }
        return tableConfig(tableId);
    }

    public Map<String, CodegenColumnView> importedColumnOverrides(String tableName) {
        String tenantId = currentTenantId();
        List<Long> tableIds = jdbcTemplate.queryForList("""
                        SELECT id FROM codegen_table
                        WHERE tenant_id = ? AND table_name = ? AND deleted = 0
                        ORDER BY updated_at DESC LIMIT 1
                        """,
                Long.class,
                tenantId,
                tableName);
        if (tableIds.isEmpty()) {
            return Map.of();
        }
        Map<String, CodegenColumnView> overrides = new LinkedHashMap<>();
        jdbcTemplate.query("""
                        SELECT * FROM codegen_table_column
                        WHERE tenant_id = ? AND table_id = ?
                        ORDER BY sort, id
                        """,
                rs -> {
                    ColumnConfigView config = columnConfigView(rs);
                    overrides.put(config.columnName(), new CodegenColumnView(
                            config.columnName(),
                            config.dataType(),
                            config.columnType(),
                            !config.required(),
                            config.primaryKey(),
                            false,
                            null,
                            config.columnComment(),
                            StringUtils.hasText(config.javaType()) ? config.javaType() : javaType(config.dataType()),
                            StringUtils.hasText(config.javaField()) ? config.javaField() : toCamel(config.columnName(), false),
                            tsType(StringUtils.hasText(config.dataType()) ? config.dataType() : "varchar")
                    ));
                },
                tenantId,
                tableIds.get(0));
        return overrides;
    }

    private Long upsertTableConfig(Long dataSourceId, CodegenTableView table, String packageName, String author) {
        String tenantId = currentTenantId();
        String className = toCamel(stripPrefix(table.tableName()), true);
        String moduleName = toCamel(stripPrefix(table.tableName()), false);
        jdbcTemplate.update("""
                        INSERT INTO codegen_table(tenant_id, data_source_id, table_name, table_comment, class_name, tpl_category, package_name, module_name, business_name, function_name, function_author, gen_type, gen_path, options, created_by, updated_by, deleted)
                        VALUES(?,?,?,?,?,'crud',?,?,?,?,?,'preview',NULL,NULL,'system','system',0)
                        ON DUPLICATE KEY UPDATE table_comment = VALUES(table_comment), class_name = VALUES(class_name), package_name = VALUES(package_name), module_name = VALUES(module_name), function_author = VALUES(function_author), updated_by = VALUES(updated_by), updated_at = NOW()
                        """,
                tenantId,
                dataSourceId,
                table.tableName(),
                table.tableComment(),
                className,
                StringUtils.hasText(packageName) ? packageName.trim() : DEFAULT_PACKAGE,
                moduleName,
                table.tableName(),
                StringUtils.hasText(table.tableComment()) ? table.tableComment() : className,
                trimToNull(author));
        return jdbcTemplate.queryForObject("""
                        SELECT id FROM codegen_table
                        WHERE tenant_id = ? AND data_source_id = ? AND table_name = ? AND deleted = 0
                        ORDER BY id DESC LIMIT 1
                        """,
                Long.class,
                tenantId,
                dataSourceId,
                table.tableName());
    }

    private void upsertColumnConfigs(Long tableId, String tableName) {
        List<CodegenColumnView> columns = rawColumns(tableName);
        int sort = 0;
        for (CodegenColumnView column : columns) {
            boolean systemColumn = SYSTEM_COLUMNS.contains(column.columnName().toLowerCase(Locale.ROOT));
            jdbcTemplate.update("""
                            INSERT INTO codegen_table_column(tenant_id, table_id, column_name, column_comment, column_type, data_type, java_type, java_field, is_pk, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, created_by, updated_by)
                            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'system','system')
                            ON DUPLICATE KEY UPDATE column_comment = VALUES(column_comment), column_type = VALUES(column_type), data_type = VALUES(data_type), is_pk = VALUES(is_pk), updated_by = VALUES(updated_by), updated_at = NOW()
                            """,
                    currentTenantId(),
                    tableId,
                    column.columnName(),
                    column.columnComment(),
                    column.columnType(),
                    column.dataType(),
                    column.javaType(),
                    column.javaField(),
                    column.primaryKey() ? 1 : 0,
                    !column.nullable() ? 1 : 0,
                    !systemColumn && !column.primaryKey() ? 1 : 0,
                    !systemColumn && !column.primaryKey() ? 1 : 0,
                    !systemColumn ? 1 : 0,
                    0,
                    "EQ",
                    defaultHtmlType(column),
                    null,
                    sort++);
        }
    }

    private CodegenTableView requireAllowlistedTable(String tableName) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM sys_codegen_allowlist
                        WHERE tenant_id = ? AND table_name = ? AND enabled = 1 AND deleted = 0
                        """,
                Long.class,
                currentTenantId(),
                tableName);
        if (count == null || count == 0) {
            throw new BusinessException("ACCESS_DENIED", "数据表未纳入代码生成白名单");
        }
        List<CodegenTableView> tables = jdbcTemplate.query("""
                        SELECT table_name, table_comment, engine, table_rows, data_length, index_length, create_time, update_time
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_name = ?
                        """,
                (rs, rowNum) -> tableView(rs),
                tableName);
        if (tables.isEmpty()) {
            throw new BusinessException("NOT_FOUND", "数据表不存在：" + tableName);
        }
        return tables.get(0);
    }

    private List<CodegenColumnView> rawColumns(String tableName) {
        return jdbcTemplate.query("""
                        SELECT column_name, data_type, column_type, is_nullable, column_key, extra, column_default, column_comment
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = ?
                        ORDER BY ordinal_position
                        """,
                (rs, rowNum) -> columnView(rs),
                tableName);
    }

    private DataSourceView dataSource(Long id) {
        return jdbcTemplate.query("""
                        SELECT id, name, jdbc_url, username, db_name, host, port, enabled, external_authorized, authorized_at, authorization_note, created_at, updated_at
                        FROM codegen_data_source
                        WHERE tenant_id = ? AND id = ? AND deleted = 0
                        """,
                (rs, rowNum) -> dataSourceView(rs),
                currentTenantId(),
                id).stream().findFirst().orElseThrow(() -> new BusinessException("NOT_FOUND", "数据源不存在"));
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
        String tenantId = currentTenantId();
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM codegen_data_source
                        WHERE tenant_id = ? AND jdbc_url = 'LOCAL' AND deleted = 0
                        """,
                Long.class,
                tenantId);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO codegen_data_source(tenant_id, name, jdbc_url, db_name, host, enabled, external_authorized, authorized_at, authorization_note, created_by, updated_by, deleted)
                        VALUES(?, '当前应用库', 'LOCAL', DATABASE(), 'LOCAL', 1, 1, CURRENT_TIMESTAMP, '当前应用库默认授权', 'system', 'system', 0)
                        """,
                tenantId);
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

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return tenantId == null || tenantId.isBlank() ? "platform" : tenantId.trim();
    }

    private String maskPassword(String password) {
        return StringUtils.hasText(password) ? "{managed}" : null;
    }

    private boolean isLocalJdbcUrl(String jdbcUrl) {
        return LOCAL_JDBC_URL.equalsIgnoreCase(jdbcUrl == null ? null : jdbcUrl.trim());
    }

    private java.sql.Timestamp timestampFromMillis(Long epochMillis) {
        return epochMillis == null ? null : java.sql.Timestamp.from(Instant.ofEpochMilli(epochMillis));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long nullableInstantMillis(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        if (timestamp == null) {
            return null;
        }
        Instant instant = timestamp.toInstant();
        return instant.toEpochMilli();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
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

    private DataSourceView dataSourceView(ResultSet rs) throws SQLException {
        String jdbcUrl = rs.getString("jdbc_url");
        boolean external = !LOCAL_JDBC_URL.equalsIgnoreCase(jdbcUrl);
        return new DataSourceView(
                rs.getLong("id"),
                rs.getString("name"),
                jdbcUrl,
                rs.getString("username"),
                rs.getString("db_name"),
                rs.getString("host"),
                (Integer) rs.getObject("port"),
                rs.getInt("enabled") == 1,
                external,
                !external || rs.getInt("external_authorized") == 1,
                nullableInstantMillis(rs, "authorized_at"),
                rs.getString("authorization_note"),
                nullableInstantMillis(rs, "created_at"),
                nullableInstantMillis(rs, "updated_at")
        );
    }

    private ImportedTableView importedTableView(ResultSet rs) throws SQLException {
        return new ImportedTableView(
                rs.getLong("id"),
                rs.getLong("data_source_id"),
                rs.getString("table_name"),
                rs.getString("table_comment"),
                rs.getString("class_name"),
                rs.getString("package_name"),
                rs.getString("module_name"),
                rs.getString("business_name"),
                rs.getString("function_name"),
                rs.getString("function_author"),
                rs.getInt("column_count"),
                nullableInstantMillis(rs, "updated_at")
        );
    }

    private ColumnConfigView columnConfigView(ResultSet rs) throws SQLException {
        return new ColumnConfigView(
                rs.getLong("id"),
                rs.getString("column_name"),
                rs.getString("column_comment"),
                rs.getString("column_type"),
                rs.getString("data_type"),
                rs.getString("java_type"),
                rs.getString("java_field"),
                rs.getInt("is_pk") == 1,
                rs.getInt("is_required") == 1,
                rs.getInt("is_insert") == 1,
                rs.getInt("is_edit") == 1,
                rs.getInt("is_list") == 1,
                rs.getInt("is_query") == 1,
                rs.getString("query_type"),
                rs.getString("html_type"),
                rs.getString("dict_type"),
                rs.getInt("sort")
        );
    }

    private String defaultHtmlType(CodegenColumnView column) {
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

    private String javaType(String dataType) {
        return switch (dataType.toLowerCase(Locale.ROOT)) {
            case "bigint" -> "Long";
            case "int", "integer", "smallint", "tinyint" -> "Integer";
            case "decimal", "numeric" -> "java.math.BigDecimal";
            case "double", "float" -> "Double";
            case "datetime", "timestamp" -> "java.time.LocalDateTime";
            case "date" -> "java.time.LocalDate";
            case "time" -> "java.time.LocalTime";
            default -> "String";
        };
    }

    private String tsType(String dataType) {
        return switch (dataType.toLowerCase(Locale.ROOT)) {
            case "bigint", "int", "integer", "smallint", "tinyint", "decimal", "numeric", "double", "float" -> "number";
            default -> "string";
        };
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
}