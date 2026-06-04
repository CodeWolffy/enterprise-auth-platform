package com.enterprise.auth.platform.modules.codegen.application;

public record CodegenArtifactDownload(
        String fileName,
        String contentType,
        byte[] payload
) {
}