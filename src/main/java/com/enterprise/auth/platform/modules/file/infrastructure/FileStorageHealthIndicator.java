package com.enterprise.auth.platform.modules.file.infrastructure;

import com.enterprise.auth.platform.modules.file.application.FileStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 文件存储健康检查（/actuator/health 中的 "fileStorage" 组件）。
 *
 * <p>MinIO 模式：探测目标 bucket 是否可达（bucketExists，等价一次 HeadBucket）；
 * 本地模式：探测存储根目录可创建且可写。探测在独立守护线程上执行并带超时保护，
 * 避免存储端无响应时拖垮整个 health 端点。</p>
 *
 * <p>位置说明：放在 modules/file/infrastructure 而非顶层 infrastructure/，
 * 因为探测依赖本模块的 {@link FileStorageProperties}、{@link MinioProperties}
 * 与 {@link MinioClient}（由 {@link MinioConfig} 条件装配）；顶层 infrastructure
 * 反向 import modules/ 会破坏既定依赖方向。</p>
 */
@Component
public class FileStorageHealthIndicator implements HealthIndicator {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(4);

    private final FileStorageProperties properties;
    private final MinioProperties minioProperties;
    private final ObjectProvider<MinioClient> minioClient;
    private final ExecutorService probeExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "file-storage-health-probe");
        thread.setDaemon(true);
        return thread;
    });

    public FileStorageHealthIndicator(
            FileStorageProperties properties,
            MinioProperties minioProperties,
            ObjectProvider<MinioClient> minioClient
    ) {
        this.properties = properties;
        this.minioProperties = minioProperties;
        this.minioClient = minioClient;
    }

    @Override
    public Health health() {
        String storage = properties.resolvedStorage();
        try {
            return CompletableFuture.supplyAsync(() -> probe(storage), probeExecutor)
                    .get(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            return Health.down()
                    .withDetail("storage", storage)
                    .withDetail("error", "storage probe timed out after " + PROBE_TIMEOUT.toSeconds() + "s")
                    .build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Health.down()
                    .withDetail("storage", storage)
                    .withDetail("error", "storage probe interrupted")
                    .build();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return down(storage, cause);
        }
    }

    private Health probe(String storage) {
        return switch (storage) {
            case "minio" -> probeMinio();
            case "local" -> probeLocal();
            default -> Health.down()
                    .withDetail("storage", storage)
                    .withDetail("error", "unsupported storage mode")
                    .build();
        };
    }

    private Health probeMinio() {
        MinioClient client = minioClient.getIfAvailable();
        if (client == null) {
            return Health.down()
                    .withDetail("storage", "minio")
                    .withDetail("error", "MinioClient bean unavailable (check platform.minio configuration)")
                    .build();
        }
        try {
            String bucket = minioProperties.resolvedBucket();
            boolean bucketExists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!bucketExists && !minioProperties.resolvedAutoCreateBucket()) {
                return Health.down()
                        .withDetail("storage", "minio")
                        .withDetail("bucket", bucket)
                        .withDetail("error", "bucket does not exist")
                        .build();
            }
            return Health.up()
                    .withDetail("storage", "minio")
                    .withDetail("bucket", bucket)
                    .withDetail("bucketExists", bucketExists)
                    .build();
        } catch (Exception exception) {
            return down("minio", exception);
        }
    }

    private Health probeLocal() {
        try {
            Path root = properties.resolvedLocalRoot().toAbsolutePath().normalize();
            Files.createDirectories(root);
            if (!Files.isWritable(root)) {
                return Health.down()
                        .withDetail("storage", "local")
                        .withDetail("root", root.toString())
                        .withDetail("error", "storage root directory is not writable")
                        .build();
            }
            return Health.up()
                    .withDetail("storage", "local")
                    .withDetail("root", root.toString())
                    .build();
        } catch (Exception exception) {
            return down("local", exception);
        }
    }

    private Health down(String storage, Throwable cause) {
        String summary = cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
        return Health.down()
                .withDetail("storage", storage)
                .withDetail("error", summary)
                .build();
    }

    @PreDestroy
    void shutdownProbeExecutor() {
        probeExecutor.shutdownNow();
    }
}
