package com.enterprise.auth.platform.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.modules.file.application.FileStorageProperties;
import com.enterprise.auth.platform.modules.file.application.ObjectStorageService.StoredObject;
import com.enterprise.auth.platform.modules.file.infrastructure.LocalObjectStorageService;
import com.enterprise.auth.platform.modules.file.infrastructure.MinioObjectStorageService;
import com.enterprise.auth.platform.modules.file.infrastructure.RoutingObjectStorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class RoutingObjectStorageServiceTest {

    @Test
    void localStorageShouldRouteUploadToLocalAdapter() {
        LocalObjectStorageService local = mock(LocalObjectStorageService.class);
        MinioObjectStorageService minio = mock(MinioObjectStorageService.class);
        when(local.put(eq("object-key"), any(InputStream.class), eq(4L), eq("image/png"), eq("a.png")))
                .thenReturn(new StoredObject("LOCAL", "local", "object-key", null));

        var routing = new RoutingObjectStorageService(
                new FileStorageProperties("local", null, null, null),
                minio,
                local
        );

        StoredObject stored = routing.put("object-key", new ByteArrayInputStream(new byte[4]), 4L, "image/png", "a.png");

        assertThat(stored.storageType()).isEqualTo("LOCAL");
        verify(local).put(eq("object-key"), any(InputStream.class), eq(4L), eq("image/png"), eq("a.png"));
    }

    @Test
    void minioStorageShouldRouteUploadToMinioAdapter() {
        LocalObjectStorageService local = mock(LocalObjectStorageService.class);
        MinioObjectStorageService minio = mock(MinioObjectStorageService.class);
        when(minio.put(eq("object-key"), any(InputStream.class), eq(4L), eq("image/png"), eq("a.png")))
                .thenReturn(new StoredObject("MINIO", "bucket", "object-key", "etag"));

        var routing = new RoutingObjectStorageService(
                new FileStorageProperties("minio", null, null, null),
                minio,
                local
        );

        StoredObject stored = routing.put("object-key", new ByteArrayInputStream(new byte[4]), 4L, "image/png", "a.png");

        assertThat(stored.storageType()).isEqualTo("MINIO");
        verify(minio).put(eq("object-key"), any(InputStream.class), eq(4L), eq("image/png"), eq("a.png"));
    }

    @Test
    void s3AliasShouldRouteUploadToMinioAdapter() {
        LocalObjectStorageService local = mock(LocalObjectStorageService.class);
        MinioObjectStorageService minio = mock(MinioObjectStorageService.class);
        when(minio.put(eq("object-key"), any(InputStream.class), eq(4L), eq("image/png"), eq("a.png")))
                .thenReturn(new StoredObject("MINIO", "bucket", "object-key", "etag"));

        var routing = new RoutingObjectStorageService(
                new FileStorageProperties("s3", null, null, null),
                minio,
                local
        );

        StoredObject stored = routing.put("object-key", new ByteArrayInputStream(new byte[4]), 4L, "image/png", "a.png");

        assertThat(stored.storageType()).isEqualTo("MINIO");
        verify(minio).put(eq("object-key"), any(InputStream.class), eq(4L), eq("image/png"), eq("a.png"));
    }

    @Test
    void downloadAndDeleteShouldRouteByBucketName() throws Exception {
        LocalObjectStorageService local = mock(LocalObjectStorageService.class);
        MinioObjectStorageService minio = mock(MinioObjectStorageService.class);
        when(local.get("local", "local-key")).thenReturn(new ByteArrayInputStream(new byte[] {1}));
        when(minio.get("bucket", "minio-key")).thenReturn(new ByteArrayInputStream(new byte[] {2}));

        var routing = new RoutingObjectStorageService(
                new FileStorageProperties("local", null, null, null),
                minio,
                local
        );

        assertThat(routing.get("local", "local-key").read()).isEqualTo(1);
        assertThat(routing.get("bucket", "minio-key").read()).isEqualTo(2);
        routing.delete("local", "local-key");
        routing.delete("bucket", "minio-key");

        verify(local).delete("local", "local-key");
        verify(minio).delete("bucket", "minio-key");
    }
}