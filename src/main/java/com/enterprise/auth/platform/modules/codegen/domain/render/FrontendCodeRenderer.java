package com.enterprise.auth.platform.modules.codegen.domain.render;

import com.enterprise.auth.platform.modules.codegen.domain.CodegenTypeMappings;
import com.enterprise.auth.platform.modules.codegen.domain.model.ColumnDefinition;
import com.enterprise.auth.platform.modules.codegen.domain.model.RenderContext;

/**
 * 前端 API 客户端与 TypeScript 类型声明的模板渲染纯逻辑。
 */
class FrontendCodeRenderer {

    String renderTypes(RenderContext model) {
        StringBuilder builder = new StringBuilder();
        builder.append("export interface ").append(model.className()).append("View {\n");
        for (ColumnDefinition column : model.columns()) {
            boolean optional = column.nullable() && !RenderSupport.isPrimaryKey(model, column);
            builder.append("  ").append(column.javaField()).append(optional ? "?: " : ": ").append(column.tsType()).append(optional ? " | null" : "").append("\n");
        }
        builder.append("}\n\n");
        builder.append("export interface ").append(model.className()).append("CreateRequest {\n");
        for (ColumnDefinition column : model.insertColumns()) {
            appendTsRequestField(builder, column);
        }
        builder.append("}\n\n");
        builder.append("export interface ").append(model.className()).append("UpdateRequest {\n");
        for (ColumnDefinition column : model.editColumns()) {
            appendTsRequestField(builder, column);
        }
        builder.append("}\n\n");
        builder.append("export interface ").append(model.className()).append("QueryParams {\n");
        builder.append("  page?: number\n  size?: number\n");
        for (ColumnDefinition column : model.queryColumns()) {
            if (RenderSupport.isBetweenQuery(column)) {
                appendTsQueryField(builder, RenderSupport.queryRangeField(column, "Start"), column);
                appendTsQueryField(builder, RenderSupport.queryRangeField(column, "End"), column);
            } else {
                appendTsQueryField(builder, column.javaField(), column);
            }
        }
        builder.append("}\n\n");
        builder.append("export interface ").append(model.className()).append("Page {\n");
        builder.append("  total: number\n  page: number\n  size: number\n  records: ").append(model.className()).append("View[]\n}\n");
        return builder.toString();
    }

    private void appendTsRequestField(StringBuilder builder, ColumnDefinition column) {
        builder.append("  ").append(column.javaField()).append(RenderSupport.requestRequired(column) ? ": " : "?: ")
                .append(column.tsType()).append(RenderSupport.requestRequired(column) ? "\n" : " | null\n");
    }

    private void appendTsQueryField(StringBuilder builder, String fieldName, ColumnDefinition column) {
        builder.append("  ").append(fieldName).append("?: ").append(column.tsType()).append(" | null\n");
    }

    String renderApi(RenderContext model) {
        String apiBase = model.tableName().startsWith("sys_") ? "/" + model.kebabName() : "/" + model.kebabName();
        return "import { requestClient } from '#/api/request';\n\n"
                + "export async function query" + model.className() + "Page(params?: any) {\n"
                + "  return requestClient.get('" + apiBase + "', { params });\n"
                + "}\n\n"
                + "export async function get" + model.className() + "(id: " + CodegenTypeMappings.tsScalarType(model.primaryKeyJavaType()) + ") {\n"
                + "  return requestClient.get('" + apiBase + "/' + id);\n"
                + "}\n\n"
                + "export async function create" + model.className() + "(payload: any) {\n"
                + "  return requestClient.post('" + apiBase + "', payload);\n"
                + "}\n\n"
                + "export async function update" + model.className() + "(id: " + CodegenTypeMappings.tsScalarType(model.primaryKeyJavaType()) + ", payload: any) {\n"
                + "  return requestClient.put('" + apiBase + "/' + id, payload);\n"
                + "}\n\n"
                + "export async function delete" + model.className() + "(id: " + CodegenTypeMappings.tsScalarType(model.primaryKeyJavaType()) + ") {\n"
                + "  return requestClient.delete('" + apiBase + "/' + id);\n"
                + "}\n";
    }
}
