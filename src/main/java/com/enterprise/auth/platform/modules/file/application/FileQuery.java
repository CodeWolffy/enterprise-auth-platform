package com.enterprise.auth.platform.modules.file.application;

import com.enterprise.auth.platform.common.web.PaginationSupport;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "文件分页查询条件")
public record FileQuery(
        @Schema(description = "文件名关键词") String keyword,
        @Schema(description = "内容类型") String contentType,
        @Schema(description = "存储类型") String storageType,
        @Schema(description = "可见性") String visibility,
        @Schema(description = "页码") int page,
        @Schema(description = "每页数量") int size
) {
    public int normalizedPage() {
        return PaginationSupport.normalizePage(page);
    }

    public int normalizedSize() {
        return PaginationSupport.normalizeSize(size, 100);
    }
}
