package com.enterprise.auth.platform.modules.codegen.domain.render;

import com.enterprise.auth.platform.modules.codegen.domain.model.ColumnDefinition;
import com.enterprise.auth.platform.modules.codegen.domain.model.RenderContext;
import java.util.List;
import java.util.Locale;

/**
 * Vben 页面片段渲染纯逻辑：Grid 列、详情项、查询表单与编辑表单 schema。
 */
class VueSnippetRenderer {

    String renderVbenGridColumns(RenderContext model) {
        StringBuilder builder = new StringBuilder();
        for (ColumnDefinition column : model.listColumns()) {
            builder.append("  { field: '")
                    .append(column.javaField())
                    .append("', minWidth: 140, title: '")
                    .append(RenderSupport.escapeTs(RenderSupport.columnLabel(column)))
                    .append("' },\n");
        }
        return builder.toString();
    }

    String renderVbenDetailItems(RenderContext model) {
        StringBuilder builder = new StringBuilder();
        for (ColumnDefinition column : model.columns()) {
            builder.append("        <div class=\"grid grid-cols-[140px_1fr] border-b border-border last:border-b-0\">\n")
                    .append("          <dt class=\"bg-muted px-4 py-3 font-medium\">")
                    .append(RenderSupport.escapeVue(RenderSupport.columnLabel(column)))
                    .append("</dt>\n")
                    .append("          <dd class=\"min-w-0 break-words px-4 py-3\">{{ detailItem.")
                    .append(column.javaField())
                    .append(" ?? '-' }}</dd>\n")
                    .append("        </div>\n");
        }
        return builder.toString();
    }

    String renderVbenSearchSchema(RenderContext model) {
        StringBuilder builder = new StringBuilder("[\n");
        for (ColumnDefinition column : model.queryColumns()) {
            if (RenderSupport.isBetweenQuery(column)) {
                appendVbenSchemaItem(builder, RenderSupport.queryRangeField(column, "Start"), column,
                        RenderSupport.columnLabel(column) + "开始", false);
                appendVbenSchemaItem(builder, RenderSupport.queryRangeField(column, "End"), column,
                        RenderSupport.columnLabel(column) + "结束", false);
            } else {
                appendVbenSchemaItem(builder, column.javaField(), column,
                        RenderSupport.columnLabel(column), false);
            }
        }
        return builder.append("]").toString();
    }

    String renderVbenFormSchema(List<ColumnDefinition> columns) {
        StringBuilder builder = new StringBuilder("[\n");
        for (ColumnDefinition column : columns) {
            appendVbenSchemaItem(builder, column.javaField(), column,
                    RenderSupport.columnLabel(column), RenderSupport.requestRequired(column));
        }
        return builder.append("]").toString();
    }

    private void appendVbenSchemaItem(
            StringBuilder builder,
            String fieldName,
            ColumnDefinition column,
            String label,
            boolean required
    ) {
        builder.append("  {\n")
                .append("    component: '").append(vbenComponent(column)).append("',\n")
                .append("    componentProps: ").append(vbenComponentProps(column, label)).append(",\n")
                .append("    fieldName: '").append(fieldName).append("',\n")
                .append("    label: '").append(RenderSupport.escapeTs(label)).append("',\n");
        if (required) {
            builder.append("    rules: 'required',\n");
        }
        if (!fieldName.endsWith("Start") && !fieldName.endsWith("End")) {
            builder.append("    defaultValue: ").append(RenderSupport.tsDefaultValue(column)).append(",\n");
        }
        builder.append("  },\n");
    }

    private String vbenComponent(ColumnDefinition column) {
        String htmlType = column.htmlType();
        if ("select".equals(htmlType)) {
            return "Select";
        }
        if ("number".equals(htmlType) || "number".equals(column.tsType())) {
            return "InputNumber";
        }
        if ("datetime".equals(htmlType) || RenderSupport.isTemporal(column)) {
            return "time".equalsIgnoreCase(column.dataType()) ? "TimePicker" : "DatePicker";
        }
        if ("boolean".equals(column.tsType())) {
            return "Switch";
        }
        return "Input";
    }

    private String vbenComponentProps(ColumnDefinition column, String label) {
        String placeholder = RenderSupport.escapeTs("请输入" + label);
        if ("select".equals(column.htmlType())) {
            return "{ clearable: true, options: " + vbenSelectOptions(column) + ", placeholder: '请选择"
                    + RenderSupport.escapeTs(label) + "' }";
        }
        if ("textarea".equals(column.htmlType())
                || (column.columnType() != null && column.columnType().toLowerCase(Locale.ROOT).contains("text"))) {
            return "{ clearable: true, placeholder: '" + placeholder + "', rows: 4, type: 'textarea' }";
        }
        if ("number".equals(column.htmlType()) || "number".equals(column.tsType())) {
            return "{ min: 0 }";
        }
        if ("datetime".equals(column.htmlType()) || RenderSupport.isTemporal(column)) {
            return vbenTemporalProps(column, label);
        }
        if ("boolean".equals(column.tsType())) {
            return "{}";
        }
        return "{ clearable: true, placeholder: '" + placeholder + "' }";
    }

    private String vbenSelectOptions(ColumnDefinition column) {
        if ("boolean".equals(column.tsType())) {
            return "[{ label: '是', value: true }, { label: '否', value: false }]";
        }
        if ("number".equals(column.tsType())) {
            return "[{ label: '选项一', value: 1 }, { label: '选项二', value: 2 }]";
        }
        return "[{ label: '选项一', value: 'option1' }, { label: '选项二', value: 'option2' }]";
    }

    private String vbenTemporalProps(ColumnDefinition column, String label) {
        String placeholder = RenderSupport.escapeTs("请选择" + label);
        return switch (column.dataType().toLowerCase(Locale.ROOT)) {
            case "date" -> "{ placeholder: '" + placeholder + "', type: 'date', valueFormat: 'YYYY-MM-DD' }";
            case "time" -> "{ placeholder: '" + placeholder + "', valueFormat: 'HH:mm:ss' }";
            default -> "{ placeholder: '" + placeholder
                    + "', type: 'datetime', valueFormat: 'YYYY-MM-DDTHH:mm:ssZ' }";
        };
    }
}
