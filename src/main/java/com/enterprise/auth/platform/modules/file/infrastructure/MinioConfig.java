package com.enterprise.auth.platform.modules.file.infrastructure;

import io.minio.MinioClient;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("'${platform.file.storage:minio}'.equalsIgnoreCase('minio') || '${platform.file.storage:minio}'.equalsIgnoreCase('s3')")
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        int connectTimeout = properties.resolvedConnectTimeoutMillis();
        int readTimeout = properties.resolvedReadTimeoutMillis();
        int writeTimeout = properties.resolvedWriteTimeoutMillis();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeout, TimeUnit.MILLISECONDS)
                .callTimeout(Math.max(connectTimeout, Math.max(readTimeout, writeTimeout)) + 5_000L, TimeUnit.MILLISECONDS)
                .build();
        return MinioClient.builder()
                .endpoint(properties.resolvedEndpoint())
                .credentials(properties.resolvedAccessKey(), properties.resolvedSecretKey())
                .region(properties.resolvedRegion())
                .httpClient(httpClient)
                .build();
    }
}