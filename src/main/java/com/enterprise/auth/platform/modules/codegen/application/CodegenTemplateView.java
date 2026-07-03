package com.enterprise.auth.platform.modules.codegen.application;

import com.enterprise.auth.platform.modules.codegen.infrastructure.entity.CodegenTemplateEntity;
import java.time.Instant;
import java.util.List;

public record CodegenTemplateView(
        Long id,
        String name,
        String language,
        String templateCategory,
        String pathPattern,
        String content,
        String description,
        boolean builtin,
        Instant createdAt,
        Instant updatedAt
) {
    public static CodegenTemplateView from(CodegenTemplateEntity entity) {
        return new CodegenTemplateView(
                entity.getId(),
                entity.getName(),
                entity.getLanguage(),
                entity.getTemplateCategory(),
                entity.getPathPattern(),
                entity.getContent(),
                entity.getDescription(),
                entity.getBuiltin() != null && entity.getBuiltin() == 1,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static List<CodegenTemplateView> fromAll(List<CodegenTemplateEntity> entities) {
        return entities.stream().map(CodegenTemplateView::from).toList();
    }
}
