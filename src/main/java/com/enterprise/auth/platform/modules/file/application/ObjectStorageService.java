package com.enterprise.auth.platform.modules.file.application;

import java.io.InputStream;

public interface ObjectStorageService {

    StoredObject put(String objectKey, InputStream inputStream, long size, String contentType, String originalName);

    InputStream get(String bucketName, String objectKey);

    void delete(String bucketName, String objectKey);

    record StoredObject(String storageType, String bucketName, String objectKey, String etag) {
    }
}