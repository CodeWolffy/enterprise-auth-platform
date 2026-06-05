package com.enterprise.auth.platform.modules.file.infrastructure;

import com.enterprise.auth.platform.common.exception.BusinessException;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "platform.minio")
public record MinioProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String region,
        String publicEndpoint,
        Boolean autoCreateBucket,
        Duration presignedUrlTtl,
        Boolean allowDefaultCredentials
) {

    public String resolvedEndpoint() {
        if (StringUtils.hasText(endpoint)) {
            return endpoint.trim();
        }
        throw new BusinessException("FILE_STORAGE_CONFIG_ERROR", "MinIO endpoint 未配置");
    }

    public String resolvedAccessKey() {
        if (StringUtils.hasText(accessKey)) {
            return accessKey.trim();
        }
        if (Boolean.TRUE.equals(allowDefaultCredentials)) {
            return "minioadmin";
        }
        throw new BusinessException("FILE_STORAGE_CONFIG_ERROR", "MinIO accessKey 未配置");
    }

    public String resolvedSecretKey() {
        if (StringUtils.hasText(secretKey)) {
            return secretKey.trim();
        }
        if (Boolean.TRUE.equals(allowDefaultCredentials)) {
            return "minioadmin";
        }
        throw new BusinessException("FILE_STORAGE_CONFIG_ERROR", "MinIO secretKey 未配置");
    }

    public String resolvedBucket() {
        if (StringUtils.hasText(bucket)) {
            return bucket.trim();
        }
        throw new BusinessException("FILE_STORAGE_CONFIG_ERROR", "MinIO bucket 未配置");
    }

    public String resolvedRegion() {
        return StringUtils.hasText(region) ? region.trim() : "us-east-1";
    }

    public String resolvedPublicEndpoint() {
        return StringUtils.hasText(publicEndpoint) ? publicEndpoint.trim() : resolvedEndpoint();
    }

    public boolean resolvedAutoCreateBucket() {
        return Boolean.TRUE.equals(autoCreateBucket);
    }

    public Duration resolvedPresignedUrlTtl() {
        return presignedUrlTtl == null || presignedUrlTtl.isZero() || presignedUrlTtl.isNegative()
                ? Duration.ofMinutes(10)
                : presignedUrlTtl;
    }
}