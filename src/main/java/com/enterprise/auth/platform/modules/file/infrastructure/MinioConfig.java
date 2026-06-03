package com.enterprise.auth.platform.modules.file.infrastructure;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
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