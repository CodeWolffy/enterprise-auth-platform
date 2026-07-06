package com.enterprise.auth.platform.modules.codegen.domain.render;

import com.enterprise.auth.platform.modules.codegen.domain.model.ColumnDefinition;
import com.enterprise.auth.platform.modules.codegen.domain.model.RenderContext;
import java.util.List;
import java.util.Locale;

/**
 * 渲染共享纯逻辑：标签、转义、查询类型判定、TS 片段等，被前后端渲染器复用。
 */
final class RenderSupport {

    private RenderSupport() {
    }

    static boolean isPrimaryKey(RenderContext model, ColumnDefinition column) {
        return column.columnName().equals(model.primaryKeyColumn());
    }

    static boolean requestRequired(ColumnDefinition column) {
        return column.required() && !column.autoIncrement();
    }

    static String columnLabel(ColumnDefinition column) {
        return column.columnComment() == null || column.columnComment().isBlank() ? column.javaField() : column.columnComment();
    }

    static boolean isColumn(ColumnDefinition column, String columnName) {
        return column.columnName().equalsIgnoreCase(columnName);
    }

    static boolean isLikeQuery(ColumnDefinition column) {
        return "LIKE".equalsIgnoreCase(column.queryType());
    }

    static boolean isBetweenQuery(ColumnDefinition column) {
        return "BETWEEN".equalsIgnoreCase(column.queryType());
    }

    static String queryRangeField(ColumnDefinition column, String suffix) {
        return column.javaField() + suffix;
    }

    static boolean isTemporal(ColumnDefinition column) {
        return switch (column.dataType().toLowerCase(Locale.ROOT)) {
            case "datetime", "timestamp", "date", "time" -> true;
            default -> false;
        };
    }

    static String tsDefaultValue(ColumnDefinition column) {
        if ("boolean".equals(column.tsType())) {
            return "false";
        }
        if ("number".equals(column.tsType())) {
            return "0";
        }
        return "''";
    }

    static String escapeJava(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String escapeVue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    static String escapeTs(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'");
    }

    static String renderTsInitialForm(RenderContext model) {
        if (model.editableColumns().isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{\n");
        for (ColumnDefinition column : model.editableColumns()) {
            builder.append("  ").append(column.javaField()).append(": ").append(tsDefaultValue(column)).append(",\n");
        }
        builder.append("}");
        return builder.toString();
    }

    static String renderTsInitialQuery(RenderContext model) {
        if (model.queryColumns().isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{\n");
        for (ColumnDefinition column : model.queryColumns()) {
            if (isBetweenQuery(column)) {
                builder.append("  ").append(queryRangeField(column, "Start")).append(": undefined,\n");
                builder.append("  ").append(queryRangeField(column, "End")).append(": undefined,\n");
            } else {
                builder.append("  ").append(column.javaField()).append(": undefined,\n");
            }
        }
        builder.append("}");
        return builder.toString();
    }

    static String renderTsPayload(List<ColumnDefinition> columns) {
        StringBuilder builder = new StringBuilder();
        builder.append("  return {\n");
        for (ColumnDefinition column : columns) {
            builder.append("    ").append(column.javaField()).append(": form.").append(column.javaField()).append(",\n");
        }
        builder.append("  }\n");
        return builder.toString();
    }

    static String renderTsRules(List<ColumnDefinition> columns) {
        StringBuilder builder = new StringBuilder("{\n");
        for (ColumnDefinition column : columns) {
            if (requestRequired(column)) {
                builder.append("  ").append(column.javaField()).append(": [{ required: true, message: '请输入")
                        .append(escapeTs(columnLabel(column)))
                        .append("', trigger: 'blur' }],\n");
            }
        }
        builder.append("}");
        return builder.toString();
    }

    static String renderTsToForm(RenderContext model) {
        StringBuilder builder = new StringBuilder();
        builder.append("  return {\n");
        for (ColumnDefinition column : model.editableColumns()) {
            builder.append("    ").append(column.javaField()).append(": row?.").append(column.javaField()).append(" ?? ").append(tsDefaultValue(column)).append(",\n");
        }
        builder.append("  }\n");
        return builder.toString();
    }
}
