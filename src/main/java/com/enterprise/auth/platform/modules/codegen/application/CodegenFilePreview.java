package com.enterprise.auth.platform.modules.codegen.application;

public record CodegenFilePreview(
        String path,
        String language,
        String content
) {
}