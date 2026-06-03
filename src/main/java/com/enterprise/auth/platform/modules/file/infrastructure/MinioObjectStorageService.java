package com.enterprise.auth.platform.modules.file.infrastructure;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.file.application.ObjectStorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.InputStream;
import org.springframework.stereotype.Service;

@Service
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioObjectStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public StoredObject put(String objectKey, InputStream inputStream, long size, String contentType, String originalName) {
        try {
            String bucket = properties.resolvedBucket();
            ensureBucket(bucket);
            var response = minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, size, -1L)
                    .contentType(contentType)
                    .headers(java.util.Map.of("x-amz-meta-original-name", safeMeta(originalName)))
                    .build());
            return new StoredObject("MINIO", bucket, objectKey, response.etag());
        } catch (Exception exception) {
            throw new BusinessException("FILE_STORAGE_ERROR", "文件写入 MinIO 失败");
        }
    }

    @Override
    public InputStream get(String bucketName, String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build());
        } catch (ErrorResponseException exception) {
            throw new BusinessException("NOT_FOUND", "文件不存在");
        } catch (Exception exception) {
            throw new BusinessException("FILE_STORAGE_ERROR", "文件读取 MinIO 失败");
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        if (!properties.resolvedAutoCreateBucket()) {
            return;
        }
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private String safeMeta(String value) {
        if (value == null || value.isBlank()) {
            return "file";
        }
        return value.length() > 120 ? value.substring(0, 120) : value;
    }
}