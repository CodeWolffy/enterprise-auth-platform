package com.enterprise.auth.platform.modules.codegen.application;

public record CodegenColumnView(
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
}