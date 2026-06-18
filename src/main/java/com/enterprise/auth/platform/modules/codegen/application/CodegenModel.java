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
        List<CodegenColumnView> editableColumns,
        List<CodegenColumnView> insertColumns,
        List<CodegenColumnView> editColumns,
        List<CodegenColumnView> listColumns,
        List<CodegenColumnView> queryColumns
) {
    CodegenModel {
        columns = columns == null ? List.of() : List.copyOf(columns);
        editableColumns = editableColumns == null ? List.of() : List.copyOf(editableColumns);
        insertColumns = insertColumns == null ? List.of() : List.copyOf(insertColumns);
        editColumns = editColumns == null ? List.of() : List.copyOf(editColumns);
        listColumns = listColumns == null ? List.of() : List.copyOf(listColumns);
        queryColumns = queryColumns == null ? List.of() : List.copyOf(queryColumns);
    }
}