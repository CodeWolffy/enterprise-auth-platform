package com.enterprise.auth.platform.modules.file.application;

import java.io.InputStream;

public record FileDownloadResult(
        String fileKey,
        String originalName,
        String contentType,
        Long size,
        InputStream stream
) {
}