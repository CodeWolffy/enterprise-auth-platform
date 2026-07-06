package com.enterprise.auth.platform.modules.codegen.domain.model;

import java.util.List;

/**
 * 模板渲染上下文：一次代码生成所需的全部命名信息与列集合。
 */
public record RenderContext(
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
        List<ColumnDefinition> columns,
        List<ColumnDefinition> editableColumns,
        List<ColumnDefinition> insertColumns,
        List<ColumnDefinition> editColumns,
        List<ColumnDefinition> listColumns,
        List<ColumnDefinition> queryColumns
) {
    public RenderContext {
        columns = columns == null ? List.of() : List.copyOf(columns);
        editableColumns = editableColumns == null ? List.of() : List.copyOf(editableColumns);
        insertColumns = insertColumns == null ? List.of() : List.copyOf(insertColumns);
        editColumns = editColumns == null ? List.of() : List.copyOf(editColumns);
        listColumns = listColumns == null ? List.of() : List.copyOf(listColumns);
        queryColumns = queryColumns == null ? List.of() : List.copyOf(queryColumns);
    }
}
