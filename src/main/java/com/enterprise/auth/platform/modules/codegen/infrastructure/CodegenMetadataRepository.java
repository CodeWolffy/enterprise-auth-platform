package com.enterprise.auth.platform.modules.codegen.infrastructure;

import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.web.PaginationSupport;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataDtos.ColumnConfigView;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataDtos.DataSourceView;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataDtos.ImportedTableView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 代码生成配置持久化仓储：codegen_data_source / codegen_table / codegen_table_column 的 JDBC 访问。
 */
@Repository
public class CodegenMetadataRepository {

    private static final String LOCAL_JDBC_URL = "LOCAL";

    private final JdbcTemplate jdbcTemplate;

    public CodegenMetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DataSourceView> findDataSources(String tenantId) {
        return jdbcTemplate.query("""
                        SELECT id, name, jdbc_url, username, db_name, host, port, enabled, external_authorized, authorized_at, authorization_note, created_at, updated_at
                        FROM codegen_data_source
                        WHERE tenant_id = ? AND deleted = 0
                        ORDER BY id
                        """,
                (rs, rowNum) -> dataSourceView(rs),
                tenantId);
    }

    public Optional<DataSourceView> findDataSource(String tenantId, Long id) {
        return jdbcTemplate.query("""
                        SELECT id, name, jdbc_url, username, db_name, host, port, enabled, external_authorized, authorized_at, authorization_note, created_at, updated_at
                        FROM codegen_data_source
                        WHERE tenant_id = ? AND id = ? AND deleted = 0
                        """,
                (rs, rowNum) -> dataSourceView(rs),
                tenantId,
                id).stream().findFirst();
    }

    public Long insertDataSource(String tenantId, String name, String jdbcUrl, String username, String passwordCipher,
                                 String dbName, String host, Integer port, int enabled, int externalAuthorized, String authorizationNote) {
        jdbcTemplate.update("""
                        INSERT INTO codegen_data_source(tenant_id, name, jdbc_url, username, password_cipher, db_name, host, port, enabled, external_authorized, authorization_note, created_by, updated_by, deleted)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?,'system','system',0)
                        """,
                tenantId, name, jdbcUrl, username, passwordCipher, dbName, host, port, enabled, externalAuthorized, authorizationNote);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void updateDataSource(String tenantId, Long id, String name, String jdbcUrl, String username, String passwordCipherOrNull,
                                 String dbName, String host, Integer port, int enabled, int externalAuthorized,
                                 Timestamp authorizedAt, String authorizationNote) {
        jdbcTemplate.update("""
                        UPDATE codegen_data_source
                        SET name = ?, jdbc_url = ?, username = ?, password_cipher = COALESCE(?, password_cipher), db_name = ?, host = ?, port = ?, enabled = ?, external_authorized = ?, authorized_at = ?, authorization_note = ?, updated_by = 'system'
                        WHERE tenant_id = ? AND id = ? AND deleted = 0
                        """,
                name, jdbcUrl, username, passwordCipherOrNull, dbName, host, port, enabled, externalAuthorized, authorizedAt, authorizationNote,
                tenantId, id);
    }

    public long countImportedTablesByDataSource(String tenantId, Long dataSourceId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM codegen_table
                        WHERE tenant_id = ? AND data_source_id = ? AND deleted = 0
                        """,
                Long.class,
                tenantId,
                dataSourceId);
        return count == null ? 0 : count;
    }

    public void softDeleteDataSource(String tenantId, Long id) {
        jdbcTemplate.update("UPDATE codegen_data_source SET deleted = 1, updated_by = 'system' WHERE tenant_id = ? AND id = ?", tenantId, id);
    }

    public void markDataSourceAuthorized(String tenantId, Long id, String note) {
        jdbcTemplate.update("""
                        UPDATE codegen_data_source
                        SET external_authorized = 1, authorized_at = UTC_TIMESTAMP(), authorization_note = ?, updated_by = 'system'
                        WHERE tenant_id = ? AND id = ? AND deleted = 0
                        """,
                note,
                tenantId,
                id);
    }

    public Integer ping() {
        return jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }

    public long countLocalDataSources(String tenantId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM codegen_data_source
                        WHERE tenant_id = ? AND jdbc_url = 'LOCAL' AND deleted = 0
                        """,
                Long.class,
                tenantId);
        return count == null ? 0 : count;
    }

    public void insertLocalDataSource(String tenantId) {
        jdbcTemplate.update("""
                        INSERT INTO codegen_data_source(tenant_id, name, jdbc_url, db_name, host, enabled, external_authorized, authorized_at, authorization_note, created_by, updated_by, deleted)
                        VALUES(?, '当前应用库', 'LOCAL', DATABASE(), 'LOCAL', 1, 1, UTC_TIMESTAMP(), '当前应用库默认授权', 'system', 'system', 0)
                        """,
                tenantId);
    }

    public PageResult<ImportedTableView> pageImportedTables(String tenantId, String keyword, int page, int size) {
        int safePage = PaginationSupport.normalizePage(page);
        int safeSize = PaginationSupport.normalizeSize(size, 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<Object> params = new ArrayList<>();
        String filterSql = " WHERE t.tenant_id = ? AND t.deleted = 0";
        params.add(tenantId);
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

    public Optional<ImportedTableView> findImportedTable(String tenantId, Long tableId) {
        return jdbcTemplate.query("""
                        SELECT t.*, (SELECT COUNT(*) FROM codegen_table_column c WHERE c.tenant_id = t.tenant_id AND c.table_id = t.id) AS column_count
                        FROM codegen_table t
                        WHERE t.tenant_id = ? AND t.id = ? AND t.deleted = 0
                        """,
                (rs, rowNum) -> importedTableView(rs),
                tenantId,
                tableId).stream().findFirst();
    }

    public List<ColumnConfigView> findColumnConfigs(String tenantId, Long tableId) {
        return jdbcTemplate.query("""
                        SELECT * FROM codegen_table_column
                        WHERE tenant_id = ? AND table_id = ?
                        ORDER BY sort, id
                        """,
                (rs, rowNum) -> columnConfigView(rs),
                tenantId,
                tableId);
    }

    public void updateColumnConfig(String tenantId, Long tableId, ColumnConfigView column) {
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
                tenantId,
                tableId,
                column.columnName());
    }

    public int softDeleteImportedTable(String tenantId, Long tableId) {
        return jdbcTemplate.update("""
                        UPDATE codegen_table
                        SET deleted = 1, updated_by = 'system'
                        WHERE tenant_id = ? AND id = ? AND deleted = 0
                        """,
                tenantId,
                tableId);
    }

    public Optional<Long> findLatestImportedTableId(String tenantId, String tableName) {
        return jdbcTemplate.queryForList("""
                        SELECT id FROM codegen_table
                        WHERE tenant_id = ? AND table_name = ? AND deleted = 0
                        ORDER BY updated_at DESC LIMIT 1
                        """,
                Long.class,
                tenantId,
                tableName).stream().findFirst();
    }

    public Long upsertTableConfig(String tenantId, Long dataSourceId, String tableName, String tableComment, String className,
                                  String packageName, String moduleName, String businessName, String functionName, String functionAuthor) {
        jdbcTemplate.update("""
                        INSERT INTO codegen_table(tenant_id, data_source_id, table_name, table_comment, class_name, tpl_category, package_name, module_name, business_name, function_name, function_author, gen_type, gen_path, options, created_by, updated_by, deleted)
                        VALUES(?,?,?,?,?,'crud',?,?,?,?,?,'preview',NULL,NULL,'system','system',0)
                        ON DUPLICATE KEY UPDATE table_comment = VALUES(table_comment), class_name = VALUES(class_name), package_name = VALUES(package_name), module_name = VALUES(module_name), function_author = VALUES(function_author), updated_by = VALUES(updated_by), updated_at = UTC_TIMESTAMP()
                        """,
                tenantId,
                dataSourceId,
                tableName,
                tableComment,
                className,
                packageName,
                moduleName,
                businessName,
                functionName,
                functionAuthor);
        return jdbcTemplate.queryForObject("""
                        SELECT id FROM codegen_table
                        WHERE tenant_id = ? AND data_source_id = ? AND table_name = ? AND deleted = 0
                        ORDER BY id DESC LIMIT 1
                        """,
                Long.class,
                tenantId,
                dataSourceId,
                tableName);
    }

    public void upsertColumnConfig(String tenantId, Long tableId, String columnName, String columnComment, String columnType,
                                   String dataType, String javaType, String javaField, int isPk, int isRequired, int isInsert,
                                   int isEdit, int isList, int isQuery, String queryType, String htmlType, String dictType, int sort) {
        jdbcTemplate.update("""
                        INSERT INTO codegen_table_column(tenant_id, table_id, column_name, column_comment, column_type, data_type, java_type, java_field, is_pk, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, created_by, updated_by)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'system','system')
                        ON DUPLICATE KEY UPDATE column_comment = VALUES(column_comment), column_type = VALUES(column_type), data_type = VALUES(data_type), is_pk = VALUES(is_pk), updated_by = VALUES(updated_by), updated_at = UTC_TIMESTAMP()
                        """,
                tenantId,
                tableId,
                columnName,
                columnComment,
                columnType,
                dataType,
                javaType,
                javaField,
                isPk,
                isRequired,
                isInsert,
                isEdit,
                isList,
                isQuery,
                queryType,
                htmlType,
                dictType,
                sort);
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
                nullableInstant(rs, "authorized_at"),
                rs.getString("authorization_note"),
                nullableInstant(rs, "created_at"),
                nullableInstant(rs, "updated_at")
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
                nullableInstant(rs, "updated_at")
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

    private Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
