package com.enterprise.auth.platform.modules.codegen.application;

import java.util.List;

public record CodegenPreviewResult(
        String tableName,
        String moduleName,
        String className,
        String generatedRoot,
        List<CodegenFilePreview> files,
        List<String> selectedFiles,
        boolean autoRegister
) {
    public CodegenPreviewResult {
        files = files == null ? List.of() : List.copyOf(files);
        selectedFiles = selectedFiles == null ? List.of() : List.copyOf(selectedFiles);
    }
}