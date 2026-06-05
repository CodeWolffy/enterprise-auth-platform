package com.enterprise.auth.platform.modules.file.infrastructure;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.file.application.FileStorageProperties;
import com.enterprise.auth.platform.modules.file.application.ObjectStorageService;
import java.io.InputStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class RoutingObjectStorageService implements ObjectStorageService {

    private final FileStorageProperties properties;
    private final ObjectProvider<MinioObjectStorageService> minioObjectStorageService;
    private final LocalObjectStorageService localObjectStorageService;

    public RoutingObjectStorageService(
            FileStorageProperties properties,
            ObjectProvider<MinioObjectStorageService> minioObjectStorageService,
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
            case "minio" -> minioStorage();
            default -> throw new BusinessException("FILE_STORAGE_ERROR", "不支持的文件存储类型");
        };
    }

    private ObjectStorageService storageByBucket(String bucketName) {
        if ("local".equalsIgnoreCase(bucketName)) {
            return localObjectStorageService;
        }
        return minioStorage();
    }

    private ObjectStorageService minioStorage() {
        MinioObjectStorageService service = minioObjectStorageService.getIfAvailable();
        if (service == null) {
            throw new BusinessException("FILE_STORAGE_CONFIG_ERROR", "MinIO 存储未启用或配置不完整");
        }
        return service;
    }
}