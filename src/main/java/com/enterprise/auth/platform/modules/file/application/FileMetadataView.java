package com.enterprise.auth.platform.modules.file.application;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "文件元数据")
public record FileMetadataView(
        @Schema(description = "文件键") String fileKey,
        @Schema(description = "原始文件名") String originalName,
        @Schema(description = "内容类型") String contentType,
        @Schema(description = "文件大小，单位字节") Long size,
        @Schema(description = "存储类型") String storageType,
        @Schema(description = "可见性") String visibility,
        @Schema(description = "所属租户") String tenantId,
        @Schema(description = "所有者用户 ID") Long ownerUserId,
        @Schema(description = "创建时间，epoch ms") Long createdAt
) {
}