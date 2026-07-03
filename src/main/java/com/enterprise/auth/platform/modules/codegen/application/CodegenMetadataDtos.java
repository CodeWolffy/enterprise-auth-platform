package com.enterprise.auth.platform.modules.codegen.application;

import java.time.Instant;
import java.util.List;

public final class CodegenMetadataDtos {

    private CodegenMetadataDtos() {
    }

    public record DataSourceView(
            Long id,
            String name,
            String jdbcUrl,
            String username,
            String dbName,
            String host,
            Integer port,
            boolean enabled,
            boolean external,
            boolean externalAuthorized,
            Instant authorizedAt,
            String authorizationNote,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DataSourceRequest(
            String name,
            String jdbcUrl,
            String username,
            String password,
            String dbName,
            String host,
            Integer port,
            Boolean enabled
    ) {
    }

    public record DataSourceAuthorizationRequest(
            String note
    ) {
    }

    public record ConnectionTestResult(
            Long dataSourceId,
            boolean success,
            String message
    ) {
    }

    public record ImportTableRequest(
            Long dataSourceId,
            List<String> tableNames,
            String packageName,
            String author
    ) {
    }

    public record ImportedTableView(
            Long id,
            Long dataSourceId,
            String tableName,
            String tableComment,
            String className,
            String packageName,
            String moduleName,
            String businessName,
            String functionName,
            String functionAuthor,
            Integer columnCount,
            Instant updatedAt
    ) {
    }

    public record ColumnConfigView(
            Long id,
            String columnName,
            String columnComment,
            String columnType,
            String dataType,
            String javaType,
            String javaField,
            boolean primaryKey,
            boolean required,
            boolean insert,
            boolean edit,
            boolean list,
            boolean query,
            String queryType,
            String htmlType,
            String dictType,
            Integer sort
    ) {
    }

    public record TableConfigDetailView(
            ImportedTableView table,
            List<ColumnConfigView> columns
    ) {
    }

    public record UpdateColumnsRequest(
            List<ColumnConfigView> columns
    ) {
    }
}
