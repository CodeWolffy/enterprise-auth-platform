package com.enterprise.auth.platform.modules.codegen.domain.render;

import com.enterprise.auth.platform.modules.codegen.domain.CodegenNaming;
import com.enterprise.auth.platform.modules.codegen.domain.model.ColumnDefinition;
import com.enterprise.auth.platform.modules.codegen.domain.model.RenderContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 后端产物模板渲染纯逻辑：Entity/Mapper/Request/ApplicationService/Controller。
 */
class BackendCodeRenderer {

    String renderEntity(RenderContext model) {
        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(model.packageName()).append(".modules.").append(model.moduleName()).append(".infrastructure.entity;\n\n");
        builder.append("import com.baomidou.mybatisplus.annotation.FieldFill;\n");
        builder.append("import com.baomidou.mybatisplus.annotation.IdType;\n");
        builder.append("import com.baomidou.mybatisplus.annotation.TableField;\n");
        builder.append("import com.baomidou.mybatisplus.annotation.TableId;\n");
        builder.append("import com.baomidou.mybatisplus.annotation.TableLogic;\n");
        builder.append("import com.baomidou.mybatisplus.annotation.TableName;\n");
        builder.append("import lombok.Data;\n\n");
        builder.append("@Data\n@TableName(\"").append(model.tableName()).append("\")\n");
        builder.append("public class ").append(model.className()).append("Entity {\n\n");
        for (ColumnDefinition column : model.columns()) {
            if (isPrimaryKey(model, column) && column.autoIncrement()) {
                builder.append("    @TableId(value = \"").append(column.columnName()).append("\", type = IdType.AUTO)\n");
            } else if (isPrimaryKey(model, column)) {
                builder.append("    @TableId(\"").append(column.columnName()).append("\")\n");
            } else if (isColumn(column, "deleted")) {
                builder.append("    @TableLogic\n");
            } else {
                String fill = fieldFill(column);
                if (fill == null) {
                    builder.append("    @TableField(\"").append(column.columnName()).append("\")\n");
                } else {
                    builder.append("    @TableField(value = \"").append(column.columnName()).append("\", fill = FieldFill.").append(fill).append(")\n");
                }
            }
            builder.append("    private ").append(column.javaType()).append(' ').append(column.javaField()).append(";\n\n");
        }
        builder.append("}\n");
        return builder.toString();
    }

    String renderMapper(RenderContext model) {
        return "package " + model.packageName() + ".modules." + model.moduleName() + ".infrastructure.mapper;\n\n"
                + "import com.baomidou.mybatisplus.core.mapper.BaseMapper;\n"
                + "import " + model.packageName() + ".modules." + model.moduleName() + ".infrastructure.entity." + model.className() + "Entity;\n"
                + "import org.apache.ibatis.annotations.Mapper;\n\n"
                + "@Mapper\n"
                + "public interface " + model.className() + "Mapper extends BaseMapper<" + model.className() + "Entity> {\n"
                + "}\n";
    }

    String renderCreateRequest(RenderContext model) {
        return renderRequestRecord(model, model.className() + "CreateRequest", model.title() + "新增请求", mutationRequestFields(model.insertColumns()));
    }

    String renderUpdateRequest(RenderContext model) {
        return renderRequestRecord(model, model.className() + "UpdateRequest", model.title() + "修改请求", mutationRequestFields(model.editColumns()));
    }

    String renderQueryRequest(RenderContext model) {
        return renderRequestRecord(model, model.className() + "QueryRequest", model.title() + "查询请求", queryRequestFields(model.queryColumns()));
    }

    private String renderRequestRecord(RenderContext model, String recordName, String description, List<String> fields) {
        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(model.packageName()).append(".modules.").append(model.moduleName()).append(".interfaces;\n\n");
        builder.append("import io.swagger.v3.oas.annotations.media.Schema;\n");
        builder.append("import jakarta.validation.constraints.NotBlank;\n");
        builder.append("import jakarta.validation.constraints.NotNull;\n\n");
        builder.append("@Schema(description = \"").append(escapeJava(description)).append("\")\n");
        builder.append("public record ").append(recordName).append('(');
        if (fields.isEmpty()) {
            builder.append(") {\n");
            builder.append("}\n");
            return builder.toString();
        }
        builder.append("\n");
        for (int i = 0; i < fields.size(); i++) {
            builder.append(fields.get(i));
            builder.append(i + 1 == fields.size() ? "\n" : ",\n");
        }
        builder.append(") {\n");
        builder.append("}\n");
        return builder.toString();
    }

    private List<String> mutationRequestFields(List<ColumnDefinition> columns) {
        List<String> fields = new ArrayList<>();
        for (ColumnDefinition column : columns) {
            fields.add(requestField(column, column.javaField(), columnLabel(column), true));
        }
        return fields;
    }

    private List<String> queryRequestFields(List<ColumnDefinition> columns) {
        List<String> fields = new ArrayList<>();
        fields.add("            @Schema(description = \"页码\") Integer page");
        fields.add("            @Schema(description = \"每页数量\") Integer size");
        for (ColumnDefinition column : columns) {
            if (isBetweenQuery(column)) {
                fields.add(requestField(column, queryRangeField(column, "Start"), columnLabel(column) + "起始", false));
                fields.add(requestField(column, queryRangeField(column, "End"), columnLabel(column) + "结束", false));
            } else {
                fields.add(requestField(column, column.javaField(), columnLabel(column), false));
            }
        }
        return fields;
    }

    private String requestField(ColumnDefinition column, String fieldName, String label, boolean validate) {
        StringBuilder builder = new StringBuilder();
        boolean required = validate && requestRequired(column);
        builder.append("            @Schema(description = \"").append(escapeJava(label)).append("\"");
        if (required) {
            builder.append(", requiredMode = Schema.RequiredMode.REQUIRED");
        }
        builder.append(") ");
        if (required) {
            builder.append("String".equals(column.javaType()) ? "@NotBlank " : "@NotNull ");
        }
        builder.append(column.javaType()).append(' ').append(fieldName);
        return builder.toString();
    }

    String renderService(RenderContext model) {
        String entity = model.className() + "Entity";
        String createRequest = model.className() + "CreateRequest";
        String updateRequest = model.className() + "UpdateRequest";
        String queryRequest = model.className() + "QueryRequest";
        String mapper = model.className() + "Mapper";
        return "package " + model.packageName() + ".modules." + model.moduleName() + ".application;\n\n"
                + "import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;\n"
                + "import com.enterprise.auth.platform.common.context.TenantContextSupport;\n"
                + "import com.enterprise.auth.platform.common.exception.BusinessException;\n"
                + "import com.enterprise.auth.platform.common.web.PageResult;\n"
                + "import com.enterprise.auth.platform.common.web.PaginationSupport;\n"
                + "import " + model.packageName() + ".modules." + model.moduleName() + ".infrastructure.entity." + entity + ";\n"
                + "import " + model.packageName() + ".modules." + model.moduleName() + ".infrastructure.mapper." + mapper + ";\n"
                + "import " + model.packageName() + ".modules." + model.moduleName() + ".interfaces." + createRequest + ";\n"
                + "import " + model.packageName() + ".modules." + model.moduleName() + ".interfaces." + updateRequest + ";\n"
                + "import " + model.packageName() + ".modules." + model.moduleName() + ".interfaces." + queryRequest + ";\n"
                + "import org.springframework.stereotype.Service;\n"
                + "import org.springframework.transaction.annotation.Transactional;\n"
                + "import org.springframework.util.StringUtils;\n\n"
                + "@Service\n"
                + "public class " + model.className() + "ApplicationService {\n\n"
                + "    private final " + mapper + " mapper;\n\n"
                + "    public " + model.className() + "ApplicationService(" + mapper + " mapper) {\n"
                + "        this.mapper = mapper;\n"
                + "    }\n\n"
                + "    @Transactional(readOnly = true)\n"
                + "    public PageResult<" + entity + "> page(" + queryRequest + " request) {\n"
                + "        int safePage = PaginationSupport.normalizePage(request == null || request.page() == null ? 1 : request.page());\n"
                + "        int safeSize = PaginationSupport.normalizeSize(request == null || request.size() == null ? 20 : request.size(), 20, 100);\n"
                + "        LambdaQueryWrapper<" + entity + "> countQuery = baseQuery(request);\n"
                + "        Long total = mapper.selectCount(countQuery);\n"
                + "        if (total == null || total == 0) {\n"
                + "            return PageResult.empty(safePage, safeSize);\n"
                + "        }\n"
                + "        LambdaQueryWrapper<" + entity + "> listQuery = baseQuery(request);\n"
                + "        applyDefaultOrder(listQuery);\n"
                + "        listQuery.last(\"limit \" + ((safePage - 1) * safeSize) + \",\" + safeSize);\n"
                + "        return PageResult.of(total, safePage, safeSize, mapper.selectList(listQuery));\n"
                + "    }\n\n"
                + "    @Transactional(readOnly = true)\n"
                + "    public " + entity + " detail(" + model.primaryKeyJavaType() + " id) {\n"
                + "        return getExisting(id);\n"
                + "    }\n\n"
                + "    @Transactional\n"
                + "    public " + entity + " create(" + createRequest + " request) {\n"
                + "        " + entity + " entity = new " + entity + "();\n"
                + renderTenantAssignment(model)
                + renderApplyRequest(model.insertColumns())
                + "        mapper.insert(entity);\n"
                + "        return entity;\n"
                + "    }\n\n"
                + "    @Transactional\n"
                + "    public " + entity + " update(" + model.primaryKeyJavaType() + " id, " + updateRequest + " request) {\n"
                + "        " + entity + " entity = getExisting(id);\n"
                + renderApplyRequest(model.editColumns())
                + "        mapper.updateById(entity);\n"
                + "        return entity;\n"
                + "    }\n\n"
                + "    @Transactional\n"
                + "    public void delete(" + model.primaryKeyJavaType() + " id) {\n"
                + "        " + entity + " entity = getExisting(id);\n"
                + "        mapper.deleteById(entity.get" + upperFirst(model.primaryKeyField()) + "());\n"
                + "    }\n\n"
                + "    private " + entity + " getExisting(" + model.primaryKeyJavaType() + " id) {\n"
                + "        " + entity + " entity = mapper.selectOne(baseQuery(null)\n"
                + "                .eq(" + entity + "::get" + upperFirst(model.primaryKeyField()) + ", id)\n"
                + "                .last(\"limit 1\"));\n"
                + "        if (entity == null) {\n"
                + "            throw new BusinessException(\"NOT_FOUND\", \"数据不存在\");\n"
                + "        }\n"
                + "        return entity;\n"
                + "    }\n\n"
                + "    private LambdaQueryWrapper<" + entity + "> baseQuery(" + queryRequest + " request) {\n"
                + "        LambdaQueryWrapper<" + entity + "> query = new LambdaQueryWrapper<>();\n"
                + renderTenantFilter(model)
                + renderDeletedFilter(model)
                + renderQueryFilters(model)
                + "        return query;\n"
                + "    }\n\n"
                + "    private void applyDefaultOrder(LambdaQueryWrapper<" + entity + "> query) {\n"
                + renderDefaultOrder(model)
                + "    }\n"
                + "}\n";
    }

    String renderController(RenderContext model) {
        String entity = model.className() + "Entity";
        String createRequest = model.className() + "CreateRequest";
        String updateRequest = model.className() + "UpdateRequest";
        String queryRequest = model.className() + "QueryRequest";
        String permissionModule = model.moduleName();
        return "package " + model.packageName() + ".modules." + model.moduleName() + ".interfaces;\n\n"
                + "import cn.dev33.satoken.annotation.SaCheckPermission;\n"
                + "import com.enterprise.auth.platform.common.web.ApiResponse;\n"
                + "import com.enterprise.auth.platform.common.web.PageResult;\n"
                + "import " + model.packageName() + ".modules." + model.moduleName() + ".application." + model.className() + "ApplicationService;\n"
                + "import " + model.packageName() + ".modules." + model.moduleName() + ".infrastructure.entity." + entity + ";\n"
                + "import io.swagger.v3.oas.annotations.Operation;\n"
                + "import io.swagger.v3.oas.annotations.Parameter;\n"
                + "import io.swagger.v3.oas.annotations.tags.Tag;\n"
                + "import jakarta.validation.Valid;\n"
                + "import org.springframework.web.bind.annotation.DeleteMapping;\n"
                + "import org.springframework.web.bind.annotation.GetMapping;\n"
                + "import org.springframework.web.bind.annotation.ModelAttribute;\n"
                + "import org.springframework.web.bind.annotation.PathVariable;\n"
                + "import org.springframework.web.bind.annotation.PostMapping;\n"
                + "import org.springframework.web.bind.annotation.PutMapping;\n"
                + "import org.springframework.web.bind.annotation.RequestBody;\n"
                + "import org.springframework.web.bind.annotation.RequestMapping;\n"
                + "import org.springframework.web.bind.annotation.RestController;\n\n"
                + "@Tag(name = \"" + escapeJava(model.title()) + "\")\n"
                + "@RestController\n"
                + "@RequestMapping(\"/api/" + model.kebabName() + "\")\n"
                + "public class " + model.className() + "Controller {\n\n"
                + "    private final " + model.className() + "ApplicationService service;\n\n"
                + "    public " + model.className() + "Controller(" + model.className() + "ApplicationService service) {\n"
                + "        this.service = service;\n"
                + "    }\n\n"
                + "    @Operation(summary = \"分页查询" + escapeJava(model.title()) + "\")\n"
                + "    @GetMapping\n"
                + "    @SaCheckPermission(\"" + permissionModule + ":page\")\n"
                + "    public ApiResponse<PageResult<" + entity + ">> page(@ModelAttribute " + queryRequest + " request) {\n"
                + "        return ApiResponse.ok(service.page(request));\n"
                + "    }\n\n"
                + "    @Operation(summary = \"查询" + escapeJava(model.title()) + "详情\")\n"
                + "    @GetMapping(\"/{id}\")\n"
                + "    @SaCheckPermission(\"" + permissionModule + ":get\")\n"
                + "    public ApiResponse<" + entity + "> detail(@Parameter(description = \"主键\") @PathVariable " + model.primaryKeyJavaType() + " id) {\n"
                + "        return ApiResponse.ok(service.detail(id));\n"
                + "    }\n\n"
                + "    @Operation(summary = \"新增" + escapeJava(model.title()) + "\")\n"
                + "    @PostMapping\n"
                + "    @SaCheckPermission(\"" + permissionModule + ":add\")\n"
                + "    public ApiResponse<" + entity + "> create(@Valid @RequestBody " + createRequest + " request) {\n"
                + "        return ApiResponse.ok(service.create(request));\n"
                + "    }\n\n"
                + "    @Operation(summary = \"修改" + escapeJava(model.title()) + "\")\n"
                + "    @PutMapping(\"/{id}\")\n"
                + "    @SaCheckPermission(\"" + permissionModule + ":edit\")\n"
                + "    public ApiResponse<" + entity + "> update(\n"
                + "            @Parameter(description = \"主键\") @PathVariable " + model.primaryKeyJavaType() + " id,\n"
                + "            @Valid @RequestBody " + updateRequest + " request\n"
                + "    ) {\n"
                + "        return ApiResponse.ok(service.update(id, request));\n"
                + "    }\n\n"
                + "    @Operation(summary = \"删除" + escapeJava(model.title()) + "\")\n"
                + "    @DeleteMapping(\"/{id}\")\n"
                + "    @SaCheckPermission(\"" + permissionModule + ":del\")\n"
                + "    public ApiResponse<Void> delete(@Parameter(description = \"主键\") @PathVariable " + model.primaryKeyJavaType() + " id) {\n"
                + "        service.delete(id);\n"
                + "        return ApiResponse.ok();\n"
                + "    }\n"
                + "}\n";
    }

    private String renderTenantAssignment(RenderContext model) {
        return model.columns().stream().anyMatch(column -> isColumn(column, "tenant_id"))
                ? "        entity.setTenantId(TenantContextSupport.currentTenantIdOrPlatform());\n"
                : "";
    }

    private String renderApplyRequest(List<ColumnDefinition> columns) {
        StringBuilder builder = new StringBuilder();
        for (ColumnDefinition column : columns) {
            builder.append("        entity.set").append(upperFirst(column.javaField())).append("(request.").append(column.javaField()).append("());\n");
        }
        return builder.toString();
    }

    private String renderTenantFilter(RenderContext model) {
        String entity = model.className() + "Entity";
        return model.columns().stream().anyMatch(column -> isColumn(column, "tenant_id"))
                ? "        query.eq(" + entity + "::getTenantId, TenantContextSupport.currentTenantIdOrPlatform());\n"
                : "";
    }

    private String renderDeletedFilter(RenderContext model) {
        String entity = model.className() + "Entity";
        return model.columns().stream().anyMatch(column -> isColumn(column, "deleted"))
                ? "        query.eq(" + entity + "::getDeleted, 0);\n"
                : "";
    }

    private String renderQueryFilters(RenderContext model) {
        if (model.queryColumns().isEmpty()) {
            return "";
        }
        String entity = model.className() + "Entity";
        StringBuilder builder = new StringBuilder();
        builder.append("        if (request != null) {\n");
        for (ColumnDefinition column : model.queryColumns()) {
            String getter = entity + "::get" + upperFirst(column.javaField());
            if (isBetweenQuery(column)) {
                String start = queryRangeField(column, "Start");
                String end = queryRangeField(column, "End");
                builder.append("            if (request.").append(start).append("() != null) {\n");
                builder.append("                query.ge(").append(getter).append(", request.").append(start).append("());\n");
                builder.append("            }\n");
                builder.append("            if (request.").append(end).append("() != null) {\n");
                builder.append("                query.le(").append(getter).append(", request.").append(end).append("());\n");
                builder.append("            }\n");
            } else if (isLikeQuery(column)) {
                if ("String".equals(column.javaType())) {
                    builder.append("            if (StringUtils.hasText(request.").append(column.javaField()).append("())) {\n");
                } else {
                    builder.append("            if (request.").append(column.javaField()).append("() != null) {\n");
                }
                builder.append("                query.like(").append(getter).append(", request.").append(column.javaField()).append("());\n");
                builder.append("            }\n");
            } else {
                if ("String".equals(column.javaType())) {
                    builder.append("            if (StringUtils.hasText(request.").append(column.javaField()).append("())) {\n");
                } else {
                    builder.append("            if (request.").append(column.javaField()).append("() != null) {\n");
                }
                builder.append("                query.eq(").append(getter).append(", request.").append(column.javaField()).append("());\n");
                builder.append("            }\n");
            }
        }
        builder.append("        }\n");
        return builder.toString();
    }

    private String renderDefaultOrder(RenderContext model) {
        String entity = model.className() + "Entity";
        ColumnDefinition orderColumn = model.columns().stream()
                .filter(column -> isColumn(column, "created_at"))
                .findFirst()
                .orElseGet(() -> model.columns().stream()
                        .filter(column -> column.columnName().equals(model.primaryKeyColumn()))
                        .findFirst()
                        .orElse(model.columns().get(0)));
        return "        query.orderByDesc(" + entity + "::get" + upperFirst(orderColumn.javaField()) + ");\n";
    }

    private String fieldFill(ColumnDefinition column) {
        String columnName = column.columnName().toLowerCase(Locale.ROOT);
        if ("created_by".equals(columnName) || "created_at".equals(columnName)) {
            return "INSERT";
        }
        if ("updated_by".equals(columnName) || "updated_at".equals(columnName)) {
            return "INSERT_UPDATE";
        }
        return null;
    }

    private boolean isPrimaryKey(RenderContext model, ColumnDefinition column) {
        return RenderSupport.isPrimaryKey(model, column);
    }

    private boolean isColumn(ColumnDefinition column, String columnName) {
        return RenderSupport.isColumn(column, columnName);
    }

    private boolean requestRequired(ColumnDefinition column) {
        return RenderSupport.requestRequired(column);
    }

    private String columnLabel(ColumnDefinition column) {
        return RenderSupport.columnLabel(column);
    }

    private boolean isLikeQuery(ColumnDefinition column) {
        return RenderSupport.isLikeQuery(column);
    }

    private boolean isBetweenQuery(ColumnDefinition column) {
        return RenderSupport.isBetweenQuery(column);
    }

    private String queryRangeField(ColumnDefinition column, String suffix) {
        return RenderSupport.queryRangeField(column, suffix);
    }

    private String escapeJava(String value) {
        return RenderSupport.escapeJava(value);
    }

    private String upperFirst(String value) {
        return CodegenNaming.upperFirst(value);
    }
}
