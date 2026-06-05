package com.enterprise.auth.platform.modules.file.infrastructure;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("'${platform.file.storage:minio}'.equalsIgnoreCase('minio') || '${platform.file.storage:minio}'.equalsIgnoreCase('s3')")
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.resolvedEndpoint())
                .credentials(properties.resolvedAccessKey(), properties.resolvedSecretKey())
                .region(properties.resolvedRegion())
                .build();
    }
}