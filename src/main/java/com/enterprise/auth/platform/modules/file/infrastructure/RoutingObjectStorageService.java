package com.enterprise.auth.platform.modules.file.infrastructure;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.file.application.FileStorageProperties;
import com.enterprise.auth.platform.modules.file.application.ObjectStorageService;
import java.io.InputStream;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class RoutingObjectStorageService implements ObjectStorageService {

    private final FileStorageProperties properties;
    private final MinioObjectStorageService minioObjectStorageService;
    private final LocalObjectStorageService localObjectStorageService;

    public RoutingObjectStorageService(
            FileStorageProperties properties,
            MinioObjectStorageService minioObjectStorageService,
            LocalObjectStorageService localObjectStorageService
    ) {
        this.properties = properties;
        this.minioObjectStorageService = minioObjectStorageService;
        this.localObjectStorageService = localObjectStorageService;
    }

    @Override
    public StoredObject put(String objectKey, InputStream inputStream, long size, String contentType, String originalName) {
        return activeStorage().put(objectKey, inputStream, size, contentType, originalName);
    }

    @Override
    public InputStream get(String bucketName, String objectKey) {
        return storageByBucket(bucketName).get(bucketName, objectKey);
    }

    @Override
    public void delete(String bucketName, String objectKey) {
        storageByBucket(bucketName).delete(bucketName, objectKey);
    }

    private ObjectStorageService activeStorage() {
        return switch (properties.resolvedStorage()) {
            case "local" -> localObjectStorageService;
            case "minio" -> minioObjectStorageService;
            default -> throw new BusinessException("FILE_STORAGE_ERROR", "不支持的文件存储类型");
        };
    }

    private ObjectStorageService storageByBucket(String bucketName) {
        if ("local".equalsIgnoreCase(bucketName)) {
            return localObjectStorageService;
        }
        return minioObjectStorageService;
    }
}