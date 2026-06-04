package com.enterprise.auth.platform.modules.codegen.interfaces;

import com.enterprise.auth.platform.modules.codegen.application.CodegenCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CodegenRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$") String tableName,
        @Size(max = 64) @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$", message = "moduleName 格式不合法") String moduleName,
        @Size(max = 200) @Pattern(regexp = "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$", message = "packageName 格式不合法") String packageName,
        @Size(max = 64) @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$", message = "className 格式不合法") String className,
        Boolean includeBackend,
        Boolean includeFrontend,
        Boolean overwrite,
        List<String> selectedFiles,
        Boolean autoRegister
) {
    public CodegenCommand toCommand() {
        return new CodegenCommand(
                tableName,
                moduleName,
                packageName,
                className,
                includeBackend == null || includeBackend,
                includeFrontend == null || includeFrontend,
                Boolean.TRUE.equals(overwrite),
                selectedFiles,
                Boolean.TRUE.equals(autoRegister)
        );
    }
}