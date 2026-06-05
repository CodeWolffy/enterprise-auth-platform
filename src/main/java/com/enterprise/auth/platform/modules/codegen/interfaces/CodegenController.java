package com.enterprise.auth.platform.modules.codegen.interfaces;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.codegen.application.CodegenApplicationService;
import com.enterprise.auth.platform.modules.codegen.application.CodegenArtifactDownload;
import com.enterprise.auth.platform.modules.codegen.application.CodegenGenerateResult;
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

    public CodegenController(CodegenApplicationService codegenApplicationService,
                             CodegenTemplateService templateService) {
        this.codegenApplicationService = codegenApplicationService;
        this.templateService = templateService;
    }

    @Operation(summary = "分页查询可生成数据表")
    @GetMapping("/tables")
    @SaCheckPermission(PermissionCodes.CODEGEN_READ)
    public ApiResponse<PageResult<CodegenTableView>> tables(
            @Parameter(description = "关键字，匹配表名或表注释") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(codegenApplicationService.tables(keyword, page, size));
    }

    @Operation(summary = "查询数据表字段")
    @GetMapping("/tables/{tableName}")
    @SaCheckPermission(PermissionCodes.CODEGEN_READ)
    public ApiResponse<CodegenTableDetailView> table(@Parameter(description = "表名") @PathVariable String tableName) {
        return ApiResponse.ok(codegenApplicationService.table(tableName));
    }

    @Operation(summary = "预览生成结果")
    @PostMapping("/preview")
    @SaCheckPermission(PermissionCodes.CODEGEN_READ)
    public ApiResponse<CodegenPreviewResult> preview(@Valid @RequestBody CodegenRequest request) {
        return ApiResponse.ok(codegenApplicationService.preview(request.toCommand()));
    }

    @Operation(summary = "生成代码到隔离目录")
    @PostMapping("/generate")
    @SaCheckPermission(PermissionCodes.CODEGEN_WRITE)
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
    @SaCheckPermission(PermissionCodes.CODEGEN_READ)
    public ApiResponse<PageResult<CodegenTemplateView>> templates(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(templateService.list(keyword, page, size));
    }

    @Operation(summary = "查询自定义模板详情")
    @GetMapping("/templates/{templateId}")
    @SaCheckPermission(PermissionCodes.CODEGEN_READ)
    public ApiResponse<CodegenTemplateView> template(@PathVariable Long templateId) {
        return ApiResponse.ok(templateService.detail(templateId));
    }

    @Operation(summary = "新增自定义模板")
    @PostMapping("/templates")
    @SaCheckPermission(PermissionCodes.CODEGEN_WRITE)
    public ApiResponse<CodegenTemplateView> createTemplate(@Valid @RequestBody CodegenTemplateRequest request) {
        return ApiResponse.ok(templateService.create(request.toView()));
    }

    @Operation(summary = "修改自定义模板")
    @PutMapping("/templates/{templateId}")
    @SaCheckPermission(PermissionCodes.CODEGEN_WRITE)
    public ApiResponse<CodegenTemplateView> updateTemplate(
            @PathVariable Long templateId,
            @Valid @RequestBody CodegenTemplateRequest request
    ) {
        return ApiResponse.ok(templateService.update(templateId, request.toView()));
    }

    @Operation(summary = "删除自定义模板")
    @DeleteMapping("/templates/{templateId}")
    @SaCheckPermission(PermissionCodes.CODEGEN_WRITE)
    public ApiResponse<Void> deleteTemplate(@PathVariable Long templateId) {
        templateService.delete(templateId);
        return ApiResponse.ok();
    }
}