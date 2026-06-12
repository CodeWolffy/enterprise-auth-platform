package com.enterprise.auth.platform.modules.codegen.interfaces;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.codegen.application.CodegenApplicationService;
import com.enterprise.auth.platform.modules.codegen.application.CodegenArtifactDownload;
import com.enterprise.auth.platform.modules.codegen.application.CodegenGenerateResult;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataDtos;
import com.enterprise.auth.platform.modules.codegen.application.CodegenMetadataService;
import com.enterprise.auth.platform.modules.codegen.application.CodegenPreviewResult;
import com.enterprise.auth.platform.modules.codegen.application.CodegenTableDetailView;
import com.enterprise.auth.platform.modules.codegen.application.CodegenTableView;
import com.enterprise.auth.platform.modules.codegen.application.CodegenTemplateService;
import com.enterprise.auth.platform.modules.codegen.application.CodegenTemplateView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "代码生成")
@RestController
@RequestMapping("/api/codegen")
public class CodegenController {

    private final CodegenApplicationService codegenApplicationService;
    private final CodegenTemplateService templateService;
    private final CodegenMetadataService metadataService;

    public CodegenController(CodegenApplicationService codegenApplicationService,
                             CodegenTemplateService templateService,
                             CodegenMetadataService metadataService) {
        this.codegenApplicationService = codegenApplicationService;
        this.templateService = templateService;
        this.metadataService = metadataService;
    }

    @Operation(summary = "查询代码生成数据源")
    @GetMapping("/datasources")
    @SaCheckPermission(PermissionCodes.CODEGEN_PAGE)
    public ApiResponse<java.util.List<CodegenMetadataDtos.DataSourceView>> dataSources() {
        return ApiResponse.ok(metadataService.dataSources());
    }

    @Operation(summary = "新增代码生成数据源")
    @PostMapping("/datasources")
    @SaCheckPermission(PermissionCodes.CODEGEN_ADD)
    public ApiResponse<CodegenMetadataDtos.DataSourceView> createDataSource(@RequestBody CodegenMetadataDtos.DataSourceRequest request) {
        return ApiResponse.ok(metadataService.createDataSource(request));
    }

    @Operation(summary = "修改代码生成数据源")
    @PutMapping("/datasources/{id}")
    @SaCheckPermission(PermissionCodes.CODEGEN_EDIT)
    public ApiResponse<CodegenMetadataDtos.DataSourceView> updateDataSource(@PathVariable Long id, @RequestBody CodegenMetadataDtos.DataSourceRequest request) {
        return ApiResponse.ok(metadataService.updateDataSource(id, request));
    }

    @Operation(summary = "删除代码生成数据源")
    @DeleteMapping("/datasources/{id}")
    @SaCheckPermission(PermissionCodes.CODEGEN_DEL)
    public ApiResponse<Void> deleteDataSource(@PathVariable Long id) {
        metadataService.deleteDataSource(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "确认外部代码生成数据源授权")
    @PostMapping("/datasources/{id}/authorize")
    @SaCheckPermission(PermissionCodes.CODEGEN_EDIT)
    public ApiResponse<CodegenMetadataDtos.DataSourceView> authorizeDataSource(
            @PathVariable Long id,
            @RequestBody(required = false) CodegenMetadataDtos.DataSourceAuthorizationRequest request
    ) {
        return ApiResponse.ok(metadataService.authorizeDataSource(id, request));
    }

    @Operation(summary = "测试代码生成数据源连接")
    @PostMapping("/datasources/{id}/test")
    @SaCheckPermission(PermissionCodes.CODEGEN_GET)
    public ApiResponse<CodegenMetadataDtos.ConnectionTestResult> testDataSource(@PathVariable Long id) {
        return ApiResponse.ok(metadataService.testConnection(id));
    }

    @Operation(summary = "查询数据源可导入数据表")
    @GetMapping("/datasources/{id}/tables")
    @SaCheckPermission(PermissionCodes.CODEGEN_PAGE)
    public ApiResponse<PageResult<CodegenTableView>> dataSourceTables(
            @PathVariable Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(metadataService.dataSourceTables(id, keyword, page, size));
    }

    @Operation(summary = "导入代码生成表配置")
    @PostMapping("/tables/import")
    @SaCheckPermission(PermissionCodes.CODEGEN_ADD)
    public ApiResponse<java.util.List<CodegenMetadataDtos.ImportedTableView>> importTables(@RequestBody CodegenMetadataDtos.ImportTableRequest request) {
        return ApiResponse.ok(metadataService.importTables(request));
    }

    @Operation(summary = "分页查询已导入表配置")
    @GetMapping("/imported-tables")
    @SaCheckPermission(PermissionCodes.CODEGEN_PAGE)
    public ApiResponse<PageResult<CodegenMetadataDtos.ImportedTableView>> importedTables(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(metadataService.importedTables(keyword, page, size));
    }

    @Operation(summary = "查询导入表字段配置")
    @GetMapping("/imported-tables/{tableId}")
    @SaCheckPermission(PermissionCodes.CODEGEN_GET)
    public ApiResponse<CodegenMetadataDtos.TableConfigDetailView> tableConfig(@PathVariable Long tableId) {
        return ApiResponse.ok(metadataService.tableConfig(tableId));
    }

    @Operation(summary = "保存导入表字段配置")
    @PutMapping("/imported-tables/{tableId}/columns")
    @SaCheckPermission(PermissionCodes.CODEGEN_EDIT)
    public ApiResponse<CodegenMetadataDtos.TableConfigDetailView> updateColumns(
            @PathVariable Long tableId,
            @RequestBody CodegenMetadataDtos.UpdateColumnsRequest request
    ) {
        return ApiResponse.ok(metadataService.updateColumns(tableId, request));
    }

    @Operation(summary = "分页查询可生成数据表")
    @GetMapping("/tables")
    @SaCheckPermission(PermissionCodes.CODEGEN_PAGE)
    public ApiResponse<PageResult<CodegenTableView>> tables(
            @Parameter(description = "关键字，匹配表名或表注释") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(codegenApplicationService.tables(keyword, page, size));
    }

    @Operation(summary = "查询数据表字段")
    @GetMapping("/tables/{tableName}")
    @SaCheckPermission(PermissionCodes.CODEGEN_GET)
    public ApiResponse<CodegenTableDetailView> table(@Parameter(description = "表名") @PathVariable String tableName) {
        return ApiResponse.ok(codegenApplicationService.table(tableName));
    }

    @Operation(summary = "预览生成结果")
    @PostMapping("/preview")
    @SaCheckPermission(PermissionCodes.CODEGEN_GET)
    public ApiResponse<CodegenPreviewResult> preview(@Valid @RequestBody CodegenRequest request) {
        return ApiResponse.ok(codegenApplicationService.preview(request.toCommand()));
    }

    @Operation(summary = "生成代码到隔离目录")
    @PostMapping("/generate")
    @SaCheckPermission(PermissionCodes.CODEGEN_ADD)
    public ApiResponse<CodegenGenerateResult> generate(@Valid @RequestBody CodegenRequest request) {
        return ApiResponse.ok(codegenApplicationService.generate(request.toCommand()));
    }

    @Operation(summary = "下载生成产物（ZIP 包）")
    @PostMapping("/download")
    @SaCheckPermission(PermissionCodes.CODEGEN_DOWNLOAD)
    public ResponseEntity<byte[]> download(@Valid @RequestBody CodegenRequest request) {
        CodegenArtifactDownload artifact = codegenApplicationService.download(request.toCommand());
        MediaType mediaType = MediaType.parseMediaType(artifact.contentType());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDispositionFormData("attachment", artifact.fileName());
        headers.setContentLength(artifact.payload().length);
        return new ResponseEntity<>(artifact.payload(), headers, org.springframework.http.HttpStatus.OK);
    }

    @Operation(summary = "分页查询自定义模板")
    @GetMapping("/templates")
    @SaCheckPermission(PermissionCodes.CODEGEN_PAGE)
    public ApiResponse<PageResult<CodegenTemplateView>> templates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String templateCategory,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(templateService.list(keyword, templateCategory, page, size));
    }

    @Operation(summary = "查询自定义模板详情")
    @GetMapping("/templates/{templateId}")
    @SaCheckPermission(PermissionCodes.CODEGEN_GET)
    public ApiResponse<CodegenTemplateView> template(@PathVariable Long templateId) {
        return ApiResponse.ok(templateService.detail(templateId));
    }

    @Operation(summary = "新增自定义模板")
    @PostMapping("/templates")
    @SaCheckPermission(PermissionCodes.CODEGEN_ADD)
    public ApiResponse<CodegenTemplateView> createTemplate(@Valid @RequestBody CodegenTemplateRequest request) {
        return ApiResponse.ok(templateService.create(request.toView()));
    }

    @Operation(summary = "修改自定义模板")
    @PutMapping("/templates/{templateId}")
    @SaCheckPermission(PermissionCodes.CODEGEN_EDIT)
    public ApiResponse<CodegenTemplateView> updateTemplate(
            @PathVariable Long templateId,
            @Valid @RequestBody CodegenTemplateRequest request
    ) {
        return ApiResponse.ok(templateService.update(templateId, request.toView()));
    }

    @Operation(summary = "删除自定义模板")
    @DeleteMapping("/templates/{templateId}")
    @SaCheckPermission(PermissionCodes.CODEGEN_DEL)
    public ApiResponse<Void> deleteTemplate(@PathVariable Long templateId) {
        templateService.delete(templateId);
        return ApiResponse.ok();
    }
}