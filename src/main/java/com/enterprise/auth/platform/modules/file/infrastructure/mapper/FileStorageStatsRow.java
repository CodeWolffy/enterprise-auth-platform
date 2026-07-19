package com.enterprise.auth.platform.modules.file.infrastructure.mapper;

import lombok.Data;

@Data
public class FileStorageStatsRow {
    private Long fileCount;
    private Long totalBytes;
}
