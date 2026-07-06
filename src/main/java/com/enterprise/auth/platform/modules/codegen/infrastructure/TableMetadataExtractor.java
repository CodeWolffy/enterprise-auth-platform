package com.enterprise.auth.platform.modules.codegen.infrastructure;

import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.web.PaginationSupport;
import com.enterprise.auth.platform.modules.codegen.domain.CodegenNaming;
import com.enterprise.auth.platform.modules.codegen.domain.CodegenTypeMappings;
import com.enterprise.auth.platform.modules.codegen.domain.model.ColumnDefinition;
import com.enterprise.auth.platform.modules.codegen.domain.model.TableDefinition;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 表元数据抽取器：从 information_schema 读取表/列元数据并映射为领域模型。
 */
@Component
public class TableMetadataExtractor {

    private final JdbcTemplate jdbcTemplate;

    public TableMetadataExtractor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 分页查询当前租户已导入代码生成配置的数据表（原 CodegenApplicationService#tables 查询部分）。
     */
    public PageResult<TableDefinition> pageImportedSourceTables(String tenantId, String keyword, int page, int size) {
        int safePage = PaginationSupport.normalizePage(page);
        int safeSize = PaginationSupport.normalizeSize(size, 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<Object> params = new ArrayList<>();
        String filterSql = """
                 WHERE t.table_schema = DATABASE()
                   AND EXISTS (
                       SELECT 1 FROM codegen_table c
                       WHERE c.tenant_id = ?
                         AND c.table_name = t.table_name
                         AND c.deleted = 0
                   )
                """;
        params.add(tenantId);
        if (!normalizedKeyword.isBlank()) {
            filterSql += " AND (t.table_name LIKE ? OR t.table_comment LIKE ?)";
            String like = "%" + normalizedKeyword + "%";
            params.add(like);
            params.add(like);
        }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables t" + filterSql, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add((safePage - 1) * safeSize);
        pageParams.add(safeSize);
        List<TableDefinition> records = jdbcTemplate.query(
                "SELECT t.table_name, t.table_comment, t.engine, t.table_rows, t.data_length, t.index_length, t.create_time, t.update_time "
                        + "FROM information_schema.tables t"
                        + filterSql
                        + " ORDER BY t.table_name LIMIT ?, ?",
                (rs, rowNum) -> tableDefinition(rs),
                pageParams.toArray()
        );
        return PageResult.of(total == null ? 0 : total, safePage, safeSize, records);
    }

    /**
     * 分页查询当前库全部数据表（原 CodegenMetadataService#dataSourceTables 查询部分）。
     */
    public PageResult<TableDefinition> pageSourceTables(String keyword, int page, int size) {
        int safePage = PaginationSupport.normalizePage(page);
        int safeSize = PaginationSupport.normalizeSize(size, 100);
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
        List<TableDefinition> records = jdbcTemplate.query(
                "SELECT table_name, table_comment, engine, table_rows, data_length, index_length, create_time, update_time "
                        + "FROM information_schema.tables"
                        + filterSql
                        + " ORDER BY table_name LIMIT ?, ?",
                (rs, rowNum) -> tableDefinition(rs),
                pageParams.toArray()
        );
        return PageResult.of(total == null ? 0 : total, safePage, safeSize, records);
    }

    /** 按表名读取单表元数据。 */
    public Optional<TableDefinition> findTable(String safeTableName) {
        List<TableDefinition> tables = jdbcTemplate.query(
                "SELECT table_name, table_comment, engine, table_rows, data_length, index_length, create_time, update_time "
                        + "FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                (rs, rowNum) -> tableDefinition(rs),
                safeTableName
        );
        return tables.stream().findFirst();
    }

    /** 读取列元数据，使用生成/预览链路的类型映射。 */
    public List<ColumnDefinition> readColumnsForGeneration(String safeTableName) {
        return readColumns(safeTableName, true);
    }

    /** 读取列元数据，使用表导入链路的类型映射。 */
    public List<ColumnDefinition> readColumnsForImport(String safeTableName) {
        return readColumns(safeTableName, false);
    }

    /** 判断表是否已导入当前租户的代码生成配置。 */
    public boolean isTableImported(String tenantId, String safeTableName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM codegen_table WHERE tenant_id = ? AND table_name = ? AND deleted = 0",
                Long.class,
                tenantId,
                safeTableName
        );
        return count != null && count > 0;
    }

    private List<ColumnDefinition> readColumns(String safeTableName, boolean generationMapping) {
        return jdbcTemplate.query(
                "SELECT column_name, data_type, column_type, is_nullable, column_key, extra, column_default, column_comment "
                        + "FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? ORDER BY ordinal_position",
                (rs, rowNum) -> columnDefinition(rs, generationMapping),
                safeTableName
        );
    }

    private TableDefinition tableDefinition(ResultSet rs) throws SQLException {
        return new TableDefinition(
                rs.getString("table_name"),
                rs.getString("table_comment"),
                rs.getString("engine"),
                nullableLong(rs, "table_rows"),
                nullableLong(rs, "data_length"),
                nullableLong(rs, "index_length"),
                nullableInstant(rs, "create_time"),
                nullableInstant(rs, "update_time")
        );
    }

    private ColumnDefinition columnDefinition(ResultSet rs, boolean generationMapping) throws SQLException {
        String columnName = rs.getString("column_name");
        String dataType = rs.getString("data_type");
        boolean primaryKey = "PRI".equalsIgnoreCase(rs.getString("column_key"));
        boolean autoIncrement = rs.getString("extra") != null && rs.getString("extra").toLowerCase(Locale.ROOT).contains("auto_increment");
        return new ColumnDefinition(
                columnName,
                dataType,
                rs.getString("column_type"),
                "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                primaryKey,
                autoIncrement,
                rs.getString("column_default"),
                rs.getString("column_comment"),
                generationMapping ? CodegenTypeMappings.generationJavaType(dataType) : CodegenTypeMappings.importJavaType(dataType),
                CodegenNaming.toCamel(columnName, false),
                generationMapping ? CodegenTypeMappings.generationTsType(dataType) : CodegenTypeMappings.importTsType(dataType)
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }
}
