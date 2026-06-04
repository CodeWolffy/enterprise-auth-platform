package com.enterprise.auth.platform.modules.codegen.interfaces;

import com.enterprise.auth.platform.modules.codegen.application.CodegenTemplateView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CodegenTemplateRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 32) @Pattern(regexp = "^(java|typescript|vue)$") String language,
        @NotBlank @Size(max = 255) String pathPattern,
        @NotBlank String content,
        @Size(max = 500) String description
) {
    public CodegenTemplateView toView() {
        return new CodegenTemplateView(null, name, language, pathPattern, content, description, false, null, null);
    }
}