package com.enterprise.auth.platform.modules.file.interfaces;

import com.enterprise.auth.platform.modules.file.domain.FileVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "文件上传选项")
public record FileUploadOptions(
        @Schema(description = "可见性：PUBLIC/TENANT/OWNER/PRIVATE，默认 OWNER") FileVisibility visibility
) {
}