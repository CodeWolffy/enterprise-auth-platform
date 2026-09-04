package com.enterprise.auth.platform.modules.codegen.domain;

import java.util.Locale;

/**
 * MySQL 数据类型到 Java/TypeScript 类型的映射纯逻辑。
 *
 * <p>统一提供标准的数据类型映射，同时保留原特定命名方法作为向后兼容委托。</p>
 */
public final class CodegenTypeMappings {

    private CodegenTypeMappings() {
    }

    /** 统一的 Java 类型映射。 */
    public static String javaType(String dataType) {
        if (dataType == null) {
            return "String";
        }
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

    /** 统一的 TypeScript 类型映射。 */
    public static String tsType(String dataType) {
        if (dataType == null) {
            return "string";
        }
        return switch (dataType.toLowerCase(Locale.ROOT)) {
            case "bigint", "int", "integer", "smallint", "tinyint", "mediumint", "decimal", "numeric", "float", "double" -> "number";
            case "bit", "boolean" -> "boolean";
            default -> "string";
        };
    }

    /** 生成/预览链路使用的 Java 类型映射（委托给统一映射）。 */
    public static String generationJavaType(String dataType) {
        return javaType(dataType);
    }

    /** 生成/预览链路使用的 TypeScript 类型映射（委托给统一映射）。 */
    public static String generationTsType(String dataType) {
        return tsType(dataType);
    }

    /** 由 Java 类型反推 TypeScript 类型，无法识别时回退到数据库类型映射。 */
    public static String generationTsTypeFromJava(String javaType, String fallbackDataType) {
        return switch (javaType) {
            case "Long", "Integer", "Double", "java.math.BigDecimal" -> "number";
            case "Boolean" -> "boolean";
            default -> tsType(fallbackDataType == null ? "varchar" : fallbackDataType);
        };
    }

    /** 主键等标量 Java 类型对应的 TypeScript 标量类型。 */
    public static String tsScalarType(String javaType) {
        return switch (javaType) {
            case "Long", "Integer", "Double", "java.math.BigDecimal" -> "number";
            case "Boolean" -> "boolean";
            default -> "string";
        };
    }

    /** 表导入链路使用的 Java 类型映射（已与 generation 链路对齐统一）。 */
    public static String importJavaType(String dataType) {
        return javaType(dataType);
    }

    /** 表导入链路使用的 TypeScript 类型映射（已与 generation 链路对齐统一）。 */
    public static String importTsType(String dataType) {
        return tsType(dataType);
    }
}
