package com.enterprise.auth.platform.modules.file.infrastructure;

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
        Duration presignedUrlTtl
) {

    public String resolvedEndpoint() {
        return StringUtils.hasText(endpoint) ? endpoint.trim() : "http://localhost:9000";
    }

    public String resolvedAccessKey() {
        return StringUtils.hasText(accessKey) ? accessKey.trim() : "minioadmin";
    }

    public String resolvedSecretKey() {
        return StringUtils.hasText(secretKey) ? secretKey.trim() : "minioadmin";
    }

    public String resolvedBucket() {
        return StringUtils.hasText(bucket) ? bucket.trim() : "enterprise-auth-platform";
    }

    public String resolvedRegion() {
        return StringUtils.hasText(region) ? region.trim() : "us-east-1";
    }

    public String resolvedPublicEndpoint() {
        return StringUtils.hasText(publicEndpoint) ? publicEndpoint.trim() : resolvedEndpoint();
    }

    public boolean resolvedAutoCreateBucket() {
        return autoCreateBucket == null || autoCreateBucket;
    }

    public Duration resolvedPresignedUrlTtl() {
        return presignedUrlTtl == null || presignedUrlTtl.isZero() || presignedUrlTtl.isNegative()
                ? Duration.ofMinutes(10)
                : presignedUrlTtl;
    }
}