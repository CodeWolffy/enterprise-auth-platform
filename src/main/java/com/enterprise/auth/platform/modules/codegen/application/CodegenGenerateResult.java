package com.enterprise.auth.platform.modules.codegen.application;

import java.util.List;

public record CodegenGenerateResult(
        String tableName,
        String moduleName,
        String outputRoot,
        List<String> files,
        List<String> registeredResourceKeys
) {
    public CodegenGenerateResult {
        files = files == null ? List.of() : List.copyOf(files);
        registeredResourceKeys = registeredResourceKeys == null ? List.of() : List.copyOf(registeredResourceKeys);
    }
}