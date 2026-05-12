package com.enterprise.auth.platform.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.io.Serializable;

@Schema(description = "通用分页结果")
public record PageResult<T>(
        @Schema(description = "总记录数") long total,
        @Schema(description = "当前页码") int page,
        @Schema(description = "每页数量") int size,
        @Schema(description = "当前页记录") List<T> records
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public static <T> PageResult<T> of(long total, int page, int size, List<T> records) {
        return new PageResult<>(total, page, size, records);
    }
}
