package com.enterprise.auth.platform.modules.file.application;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "platform.file")
public record FileStorageProperties(
        String storage,
        DataSize maxSize,
        List<String> allowedTypes,
        Path localRoot
) {

    public String resolvedStorage() {
        String value = StringUtils.hasText(storage) ? storage.trim().toLowerCase() : "minio";
        return "s3".equals(value) ? "minio" : value;
    }

    public long resolvedMaxSizeBytes() {
        return maxSize == null ? DataSize.ofMegabytes(10).toBytes() : maxSize.toBytes();
    }

    public List<String> resolvedAllowedTypes() {
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            return List.of("image/png", "image/jpeg", "application/pdf");
        }
        return allowedTypes.stream()
                .filter(StringUtils::hasText)
                .map(type -> type.trim().toLowerCase())
                .toList();
    }

    public Path resolvedLocalRoot() {
        return localRoot == null ? Path.of("data", "files") : localRoot;
    }
}