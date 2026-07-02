package com.enterprise.auth.platform.modules.file.interfaces;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.log.infrastructure.annotation.SysLog;
import com.enterprise.auth.platform.modules.file.application.FileApplicationService;
import com.enterprise.auth.platform.modules.file.application.FileDownloadResult;
import com.enterprise.auth.platform.modules.file.application.FileMetadataView;
import com.enterprise.auth.platform.modules.file.application.FileQuery;
import com.enterprise.auth.platform.modules.file.domain.FileVisibility;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "文件管理")
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileApplicationService fileApplicationService;

    public FileController(FileApplicationService fileApplicationService) {
        this.fileApplicationService = fileApplicationService;
    }

    @Operation(summary = "分页查询文件")
    @GetMapping
    @SaCheckPermission(PermissionCodes.FILE_PAGE)
    public ApiResponse<PageResult<FileMetadataView>> page(
            @Parameter(description = "文件名关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "内容类型") @RequestParam(required = false) String contentType,
            @Parameter(description = "存储类型") @RequestParam(required = false) String storageType,
            @Parameter(description = "可见性") @RequestParam(required = false) String visibility,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(fileApplicationService.page(new FileQuery(keyword, contentType, storageType, visibility, page, size)));
    }

    @SysLog("上传文件")
    @Operation(summary = "上传文件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission(PermissionCodes.FILE_ADD)
    public ApiResponse<FileMetadataView> upload(
            @Parameter(description = "文件") @RequestPart("file") MultipartFile file,
            @Parameter(description = "可见性：PUBLIC/TENANT/OWNER/PRIVATE，默认 OWNER") @RequestParam(required = false) FileVisibility visibility
    ) {
        return ApiResponse.ok(fileApplicationService.upload(file, visibility));
    }

    @Operation(summary = "查询文件元数据")
    @GetMapping("/{fileKey}/metadata")
    public ApiResponse<FileMetadataView> metadata(@PathVariable String fileKey) {
        return ApiResponse.ok(fileApplicationService.metadata(fileKey));
    }

    @Operation(summary = "读取文件")
    @GetMapping("/{fileKey}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String fileKey) {
        return downloadResponse(fileApplicationService.download(fileKey));
    }

    @Operation(summary = "读取公开文件")
    @GetMapping("/public/{fileKey}")
    public ResponseEntity<InputStreamResource> publicDownload(@PathVariable String fileKey) {
        return downloadResponse(fileApplicationService.publicDownload(fileKey));
    }

    @SysLog("删除文件")
    @Operation(summary = "删除文件")
    @DeleteMapping("/{fileKey}")
    @SaCheckPermission(PermissionCodes.FILE_DEL)
    public ApiResponse<Void> delete(@PathVariable String fileKey) {
        fileApplicationService.delete(fileKey);
        return ApiResponse.ok();
    }

    private ResponseEntity<InputStreamResource> downloadResponse(FileDownloadResult result) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.contentType()));
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(safeFilename(result.originalName()), StandardCharsets.UTF_8)
                .build());
        if (result.size() != null && result.size() >= 0) {
            headers.setContentLength(result.size());
        }
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(result.stream()));
    }

    private String safeFilename(String filename) {
        return filename == null || filename.isBlank() ? "file" : filename;
    }
}
