package com.enterprise.auth.platform.modules.codegen.application;

import java.util.List;

record CodegenModel(
        String tableName,
        String moduleName,
        String packageName,
        String className,
        String lowerClassName,
        String kebabName,
        String title,
        String primaryKeyColumn,
        String primaryKeyField,
        String primaryKeyJavaType,
        List<CodegenColumnView> columns,
        List<CodegenColumnView> editableColumns
) {
    CodegenModel {
        columns = columns == null ? List.of() : List.copyOf(columns);
        editableColumns = editableColumns == null ? List.of() : List.copyOf(editableColumns);
    }
}