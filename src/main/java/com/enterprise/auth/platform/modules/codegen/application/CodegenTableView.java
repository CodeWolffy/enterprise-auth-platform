package com.enterprise.auth.platform.modules.codegen.application;

import java.util.List;

public record CodegenTableView(
        String tableName,
        String tableComment,
        String engine,
        Long tableRows,
        Long dataLength,
        Long indexLength,
        Long createdAt,
        Long updatedAt
) {
}