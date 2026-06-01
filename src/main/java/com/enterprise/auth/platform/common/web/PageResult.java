package com.enterprise.auth.platform.common.web;

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

    public PageResult {
        records = records == null ? List.of() : List.copyOf(records);
        page = Math.max(page, 1);
        size = Math.max(size, 0);
        total = Math.max(total, 0);
    }

    public static <T> PageResult<T> of(long total, int page, int size, List<T> records) {
        return new PageResult<>(total, page, size, records);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(0, page, size, List.of());
    }
}