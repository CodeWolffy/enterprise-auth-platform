package com.enterprise.auth.platform.modules.codegen.domain.model;

import java.time.Instant;

/**
 * 数据表元数据领域模型（information_schema.tables 的纯业务映射）。
 */
public record TableDefinition(
        String tableName,
        String tableComment,
        String engine,
        Long tableRows,
        Long dataLength,
        Long indexLength,
        Instant createdAt,
        Instant updatedAt
) {
}
