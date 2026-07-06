package com.enterprise.auth.platform.modules.codegen.domain.render;

import com.enterprise.auth.platform.modules.codegen.domain.model.ColumnDefinition;
import com.enterprise.auth.platform.modules.codegen.domain.model.RenderContext;
import java.util.Locale;

/**
 * Vue 模板片段渲染纯逻辑：表格列、详情描述、表单项、搜索项与输入控件。
 */
class VueSnippetRenderer {

    String renderVueColumns(RenderContext model) {
        StringBuilder builder = new StringBuilder();
        for (ColumnDefinition column : model.listColumns()) {
            builder.append("        <el-table-column prop=\"")
                    .append(column.javaField())
                    .append("\" label=\"")
                    .append(RenderSupport.escapeVue(RenderSupport.columnLabel(column)))
                    .append("\" min-width=\"140\" show-overflow-tooltip />\n");
        }
        return builder.toString();
    }

    String renderVueDescriptions(RenderContext model) {
        StringBuilder builder = new StringBuilder();
        for (ColumnDefinition column : model.columns()) {
            builder.append("        <el-descriptions-item label=\"")
                    .append(RenderSupport.escapeVue(RenderSupport.columnLabel(column)))
                    .append("\">{{ detailItem.")
                    .append(column.javaField())
                    .append(" }}</el-descriptions-item>\n");
        }
        return builder.toString();
    }

    String renderVueFormItems(RenderContext model) {
        StringBuilder builder = new StringBuilder();
        for (ColumnDefinition column : model.editableColumns()) {
            builder.append("        <el-form-item")
                    .append(vueFormItemVisibility(column))
                    .append(" label=\"")
                    .append(RenderSupport.escapeVue(RenderSupport.columnLabel(column)))
                    .append("\" prop=\"")
                    .append(column.javaField())
                    .append("\">\n")
                    .append(vueInput("form", column))
                    .append("        </el-form-item>\n");
        }
        return builder.toString();
    }

    private String vueFormItemVisibility(ColumnDefinition column) {
        if (column.insert() && column.edit()) {
            return "";
        }
        if (column.insert()) {
            return " v-if=\"editingId === null\"";
        }
        return " v-if=\"editingId !== null\"";
    }

    String renderVueSearchItems(RenderContext model) {
        if (model.queryColumns().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ColumnDefinition column : model.queryColumns()) {
            if (RenderSupport.isBetweenQuery(column)) {
                builder.append("        <el-form-item label=\"").append(RenderSupport.escapeVue(RenderSupport.columnLabel(column))).append("\">\n");
                builder.append("          <div style=\"display: flex; gap: 8px\">\n");
                builder.append(queryInput(RenderSupport.queryRangeField(column, "Start"), column, "开始"));
                builder.append(queryInput(RenderSupport.queryRangeField(column, "End"), column, "结束"));
                builder.append("          </div>\n");
                builder.append("        </el-form-item>\n");
            } else {
                builder.append("        <el-form-item label=\"").append(RenderSupport.escapeVue(RenderSupport.columnLabel(column))).append("\">\n");
                builder.append(queryInput(column.javaField(), column, "请输入" + RenderSupport.columnLabel(column)));
                builder.append("        </el-form-item>\n");
            }
        }
        return builder.toString();
    }

    private String queryInput(String fieldName, ColumnDefinition column, String placeholder) {
        return vueInput("query", fieldName, column, placeholder);
    }

    private String vueInput(String modelName, ColumnDefinition column) {
        return vueInput(modelName, column.javaField(), column, "请输入" + RenderSupport.columnLabel(column));
    }

    private String vueInput(String modelName, String fieldName, ColumnDefinition column, String placeholder) {
        String modelPath = modelName + "." + fieldName;
        String htmlType = column.htmlType();
        if ("select".equals(htmlType)) {
            return "          <el-select v-model=\"" + modelPath + "\" placeholder=\"" + RenderSupport.escapeVue(placeholder) + "\" clearable style=\"width: 100%\">\n"
                    + selectOptions(column)
                    + "          </el-select>\n";
        }
        if ("textarea".equals(htmlType)) {
            return "          <el-input v-model=\"" + modelPath + "\" type=\"textarea\" :rows=\"4\" placeholder=\"" + RenderSupport.escapeVue(placeholder) + "\" clearable />\n";
        }
        if ("number".equals(htmlType)) {
            return "          <el-input-number v-model=\"" + modelPath + "\" :min=\"0\" controls-position=\"right\" style=\"width: 100%\" />\n";
        }
        if ("datetime".equals(htmlType)) {
            return renderTemporalControl(modelPath, placeholder, column);
        }
        if ("boolean".equals(column.tsType())) {
            return "          <el-switch v-model=\"" + modelPath + "\" />\n";
        }
        if ("number".equals(column.tsType())) {
            return "          <el-input-number v-model=\"" + modelPath + "\" :min=\"0\" controls-position=\"right\" style=\"width: 100%\" />\n";
        }
        if (RenderSupport.isTemporal(column)) {
            return renderTemporalControl(modelPath, placeholder, column);
        }
        if (column.columnType() != null && column.columnType().toLowerCase(Locale.ROOT).contains("text")) {
            return "          <el-input v-model=\"" + modelPath + "\" type=\"textarea\" :rows=\"4\" placeholder=\"" + RenderSupport.escapeVue(placeholder) + "\" clearable />\n";
        }
        return "          <el-input v-model=\"" + modelPath + "\" placeholder=\"" + RenderSupport.escapeVue(placeholder) + "\" clearable />\n";
    }

    private String selectOptions(ColumnDefinition column) {
        if ("boolean".equals(column.tsType())) {
            return "            <el-option label=\"是\" :value=\"true\" />\n"
                    + "            <el-option label=\"否\" :value=\"false\" />\n";
        }
        if ("number".equals(column.tsType())) {
            return "            <el-option label=\"选项一\" :value=\"1\" />\n"
                    + "            <el-option label=\"选项二\" :value=\"2\" />\n";
        }
        return "            <el-option label=\"选项一\" value=\"option1\" />\n"
                + "            <el-option label=\"选项二\" value=\"option2\" />\n";
    }

    private String renderTemporalControl(String modelPath, String placeholder, ColumnDefinition column) {
        return switch (column.dataType().toLowerCase(Locale.ROOT)) {
            case "date" -> "          <el-date-picker v-model=\"" + modelPath + "\" type=\"date\" value-format=\"YYYY-MM-DD\" placeholder=\"" + RenderSupport.escapeVue(placeholder) + "\" style=\"width: 100%\" />\n";
            case "time" -> "          <el-time-picker v-model=\"" + modelPath + "\" value-format=\"HH:mm:ss\" placeholder=\"" + RenderSupport.escapeVue(placeholder) + "\" style=\"width: 100%\" />\n";
            default -> "          <el-date-picker v-model=\"" + modelPath + "\" type=\"datetime\" value-format=\"YYYY-MM-DDTHH:mm:ssZ\" placeholder=\"" + RenderSupport.escapeVue(placeholder) + "\" style=\"width: 100%\" />\n";
        };
    }
}
