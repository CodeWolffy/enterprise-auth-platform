package com.enterprise.auth.platform.modules.codegen.application;

import java.util.List;

public record CodegenCommand(
        String tableName,
        String moduleName,
        String packageName,
        String className,
        boolean includeBackend,
        boolean includeFrontend,
        boolean overwrite,
        List<String> selectedFiles,
        boolean autoRegister
) {
    public CodegenCommand {
        moduleName = normalize(moduleName);
        packageName = normalize(packageName);
        className = normalize(className);
        selectedFiles = selectedFiles == null ? null : List.copyOf(selectedFiles);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}