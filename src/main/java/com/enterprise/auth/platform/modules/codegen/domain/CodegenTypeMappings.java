package com.enterprise.auth.platform.modules.codegen.domain;

import java.util.Locale;

/**
 * MySQL 数据类型到 Java/TypeScript 类型的映射纯逻辑。
 *
 * <p>历史上生成链路（原 CodegenApplicationService）与导入链路（原 CodegenMetadataService）
 * 的映射表并不一致（generation 变体额外识别 mediumint/bit/boolean），为保持行为不变，
 * 两个变体均原样保留。</p>
 */
public final class CodegenTypeMappings {

    private CodegenTypeMappings() {
    }

    /** 生成/预览链路使用的 Java 类型映射（原 CodegenApplicationService#javaType）。 */
    public static String generationJavaType(String dataType) {
        return switch (dataType.toLowerCase(Locale.ROOT)) {
            case "bigint" -> "Long";
            case "int", "integer", "smallint", "tinyint", "mediumint" -> "Integer";
            case "decimal", "numeric" -> "java.math.BigDecimal";
            case "float", "double" -> "Double";
            case "datetime", "timestamp" -> "java.time.Instant";
            case "date" -> "java.time.LocalDate";
            case "time" -> "java.time.LocalTime";
            case "bit", "boolean" -> "Boolean";
            default -> "String";
        };
    }

    /** 生成/预览链路使用的 TypeScript 类型映射（原 CodegenApplicationService#tsType）。 */
    public static String generationTsType(String dataType) {
        return switch (dataType.toLowerCase(Locale.ROOT)) {
            case "bigint", "int", "integer", "smallint", "tinyint", "mediumint", "decimal", "numeric", "float", "double" -> "number";
            case "bit", "boolean" -> "boolean";
            default -> "string";
        };
    }

    /** 由 Java 类型反推 TypeScript 类型，无法识别时回退到数据库类型映射（原 tsTypeFromJava）。 */
    public static String generationTsTypeFromJava(String javaType, String fallbackDataType) {
        return switch (javaType) {
            case "Long", "Integer", "Double", "java.math.BigDecimal" -> "number";
            case "Boolean" -> "boolean";
            default -> generationTsType(fallbackDataType == null ? "varchar" : fallbackDataType);
        };
    }

    /** 主键等标量 Java 类型对应的 TypeScript 标量类型（原 tsScalarType）。 */
    public static String tsScalarType(String javaType) {
        return switch (javaType) {
            case "Long", "Integer", "Double", "java.math.BigDecimal" -> "number";
            case "Boolean" -> "boolean";
            default -> "string";
        };
    }

    /** 表导入链路使用的 Java 类型映射（原 CodegenMetadataService#javaType）。 */
    public static String importJavaType(String dataType) {
        return switch (dataType.toLowerCase(Locale.ROOT)) {
            case "bigint" -> "Long";
            case "int", "integer", "smallint", "tinyint" -> "Integer";
            case "decimal", "numeric" -> "java.math.BigDecimal";
            case "double", "float" -> "Double";
            case "datetime", "timestamp" -> "java.time.Instant";
            case "date" -> "java.time.LocalDate";
            case "time" -> "java.time.LocalTime";
            default -> "String";
        };
    }

    /** 表导入链路使用的 TypeScript 类型映射（原 CodegenMetadataService#tsType）。 */
    public static String importTsType(String dataType) {
        return switch (dataType.toLowerCase(Locale.ROOT)) {
            case "bigint", "int", "integer", "smallint", "tinyint", "decimal", "numeric", "double", "float" -> "number";
            default -> "string";
        };
    }
}
