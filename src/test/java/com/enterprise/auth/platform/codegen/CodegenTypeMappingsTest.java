package com.enterprise.auth.platform.codegen;

import com.enterprise.auth.platform.modules.codegen.domain.CodegenTypeMappings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CodegenTypeMappingsTest {

    @ParameterizedTest
    @CsvSource({
            "bigint, Long, number",
            "int, Integer, number",
            "integer, Integer, number",
            "smallint, Integer, number",
            "tinyint, Integer, number",
            "mediumint, Integer, number",
            "decimal, java.math.BigDecimal, number",
            "numeric, java.math.BigDecimal, number",
            "float, Double, number",
            "double, Double, number",
            "datetime, java.time.Instant, string",
            "timestamp, java.time.Instant, string",
            "date, java.time.LocalDate, string",
            "time, java.time.LocalTime, string",
            "bit, Boolean, boolean",
            "boolean, Boolean, boolean",
            "varchar, String, string",
            "char, String, string",
            "text, String, string",
            "unknown_type, String, string"
    })
    void shouldMapTypesConsistentlyAcrossBothChains(String dbType, String expectedJavaType, String expectedTsType) {
        assertThat(CodegenTypeMappings.javaType(dbType)).isEqualTo(expectedJavaType);
        assertThat(CodegenTypeMappings.generationJavaType(dbType)).isEqualTo(expectedJavaType);
        assertThat(CodegenTypeMappings.importJavaType(dbType)).isEqualTo(expectedJavaType);

        assertThat(CodegenTypeMappings.tsType(dbType)).isEqualTo(expectedTsType);
        assertThat(CodegenTypeMappings.generationTsType(dbType)).isEqualTo(expectedTsType);
        assertThat(CodegenTypeMappings.importTsType(dbType)).isEqualTo(expectedTsType);
    }

    @Test
    void shouldHandleNullGracefully() {
        assertThat(CodegenTypeMappings.javaType(null)).isEqualTo("String");
        assertThat(CodegenTypeMappings.tsType(null)).isEqualTo("string");
    }

    @Test
    void shouldDeriveTsTypeFromJava() {
        assertThat(CodegenTypeMappings.generationTsTypeFromJava("Long", "bigint")).isEqualTo("number");
        assertThat(CodegenTypeMappings.generationTsTypeFromJava("Integer", "int")).isEqualTo("number");
        assertThat(CodegenTypeMappings.generationTsTypeFromJava("Double", "float")).isEqualTo("number");
        assertThat(CodegenTypeMappings.generationTsTypeFromJava("java.math.BigDecimal", "decimal")).isEqualTo("number");
        assertThat(CodegenTypeMappings.generationTsTypeFromJava("Boolean", "bit")).isEqualTo("boolean");
        assertThat(CodegenTypeMappings.generationTsTypeFromJava("String", "varchar")).isEqualTo("string");
        assertThat(CodegenTypeMappings.generationTsTypeFromJava("Custom", "int")).isEqualTo("number");
    }

    @Test
    void shouldResolveTsScalarType() {
        assertThat(CodegenTypeMappings.tsScalarType("Long")).isEqualTo("number");
        assertThat(CodegenTypeMappings.tsScalarType("Integer")).isEqualTo("number");
        assertThat(CodegenTypeMappings.tsScalarType("Boolean")).isEqualTo("boolean");
        assertThat(CodegenTypeMappings.tsScalarType("String")).isEqualTo("string");
    }
}
