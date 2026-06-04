package com.enterprise.auth.platform.modules.file.infrastructure;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.file.application.FileStorageProperties;
import com.enterprise.auth.platform.modules.file.application.ObjectStorageService;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LocalObjectStorageService implements ObjectStorageService {

    private static final String BUCKET_NAME = "local";

    private final FileStorageProperties properties;

    public LocalObjectStorageService(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredObject put(String objectKey, InputStream inputStream, long size, String contentType, String originalName) {
        try {
            Path root = properties.resolvedLocalRoot().toAbsolutePath().normalize();
            Path target = root.resolve(objectKey).normalize();
            if (!target.startsWith(root)) {
                throw new BusinessException("VALIDATION_ERROR", "文件路径非法");
            }
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredObject("LOCAL", BUCKET_NAME, objectKey, null);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("FILE_STORAGE_ERROR", "文件写入本地存储失败");
        }
    }

    @Override
    public InputStream get(String bucketName, String objectKey) {
        try {
            Path root = properties.resolvedLocalRoot().toAbsolutePath().normalize();
            Path target = root.resolve(objectKey).normalize();
            if (!target.startsWith(root) || !Files.isRegularFile(target)) {
                throw new BusinessException("NOT_FOUND", "文件不存在");
            }
            return Files.newInputStream(target);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("FILE_STORAGE_ERROR", "文件读取本地存储失败");
        }
    }

    @Override
    public void delete(String bucketName, String objectKey) {
        try {
            Path root = properties.resolvedLocalRoot().toAbsolutePath().normalize();
            Path target = root.resolve(objectKey).normalize();
            if (target.startsWith(root) && StringUtils.hasText(objectKey)) {
                Files.deleteIfExists(target);
            }
        } catch (Exception exception) {
            throw new BusinessException("FILE_STORAGE_ERROR", "文件删除失败");
        }
    }
}