package com.enterprise.auth.platform.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Schema(description = "公告新增或修改请求")
public record NoticeCrudRequest(
        @Schema(description = "公告标题", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String noticeTitle,
        @Schema(description = "公告内容", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String noticeContent,
        @Schema(description = "是否发布") Boolean published,
        @Schema(description = "发布时间") LocalDateTime publishTime
) {
}
