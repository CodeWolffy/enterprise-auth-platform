package com.enterprise.auth.platform.modules.codegen.application;

public record CodegenCommand(
        String tableName,
        String moduleName,
        String packageName,
        String className,
        boolean includeBackend,
        boolean includeFrontend,
        boolean overwrite
) {
    public CodegenCommand {
        moduleName = normalize(moduleName);
        packageName = normalize(packageName);
        className = normalize(className);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}