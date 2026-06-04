package com.enterprise.auth.platform.modules.codegen.application;

import java.util.List;

public record CodegenGenerateResult(
        String tableName,
        String moduleName,
        String outputRoot,
        List<String> files
) {
    public CodegenGenerateResult {
        files = files == null ? List.of() : List.copyOf(files);
    }
}