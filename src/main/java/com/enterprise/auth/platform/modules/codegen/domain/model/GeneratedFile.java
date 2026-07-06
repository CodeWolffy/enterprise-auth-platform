package com.enterprise.auth.platform.modules.codegen.domain.model;

/**
 * 代码生成产物领域模型：相对路径 + 语言 + 文件内容。
 */
public record GeneratedFile(
        String path,
        String language,
        String content
) {
}
