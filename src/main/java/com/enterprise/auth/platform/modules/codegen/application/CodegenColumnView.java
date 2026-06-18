package com.enterprise.auth.platform.modules.codegen.application;

import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

public record CodegenColumnView(
        String columnName,
        String dataType,
        String columnType,
        boolean nullable,
        boolean primaryKey,
        boolean autoIncrement,
        boolean required,
        String columnDefault,
        String columnComment,
        String javaType,
        String javaField,
        String tsType,
        boolean insert,
        boolean edit,
        boolean list,
        boolean query,
        String queryType,
        String htmlType,
        String dictType
) {
    private static final Set<String> SYSTEM_COLUMNS = Set.of(
            "id", "tenant_id", "created_by", "updated_by", "deleted", "created_at", "updated_at"
    );

    public CodegenColumnView(
            String columnName,
            String dataType,
            String columnType,
            boolean nullable,
            boolean primaryKey,
            boolean autoIncrement,
            String columnDefault,
            String columnComment,
            String javaType,
            String javaField,
            String tsType
    ) {
        this(
                columnName,
                dataType,
                columnType,
                nullable,
                primaryKey,
                autoIncrement,
                !nullable && columnDefault == null && !autoIncrement,
                columnDefault,
                columnComment,
                javaType,
                javaField,
                tsType,
                !primaryKey && !isSystemColumn(columnName),
                !primaryKey && !isSystemColumn(columnName),
                !primaryKey && !isSystemColumn(columnName),
                !primaryKey && !isSystemColumn(columnName) && "String".equals(javaType),
                "LIKE",
                null,
                null
        );
    }

    public CodegenColumnView {
        queryType = StringUtils.hasText(queryType) ? queryType.trim().toUpperCase() : "EQ";
        htmlType = StringUtils.hasText(htmlType) ? htmlType.trim().toLowerCase() : null;
        dictType = StringUtils.hasText(dictType) ? dictType.trim() : null;
    }

    private static boolean isSystemColumn(String columnName) {
        return columnName != null && SYSTEM_COLUMNS.contains(columnName.toLowerCase(Locale.ROOT));
    }
}
