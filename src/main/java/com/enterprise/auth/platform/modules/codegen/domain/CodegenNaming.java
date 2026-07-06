package com.enterprise.auth.platform.modules.codegen.domain;

import java.util.List;
import java.util.Locale;

/**
 * 命名转换纯逻辑：下划线转驼峰、驼峰转 kebab、表前缀剥离、首字母大写。
 */
public final class CodegenNaming {

    private CodegenNaming() {
    }

    public static String stripPrefix(String tableName) {
        String value = tableName;
        for (String prefix : List.of("sys_", "wf_")) {
            if (value.startsWith(prefix)) {
                return value.substring(prefix.length());
            }
        }
        return value;
    }

    public static String toCamel(String value, boolean upperFirst) {
        StringBuilder builder = new StringBuilder();
        for (String part : value.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        if (builder.isEmpty()) {
            return upperFirst ? "Generated" : "generated";
        }
        if (!upperFirst) {
            builder.setCharAt(0, Character.toLowerCase(builder.charAt(0)));
        }
        return builder.toString();
    }

    public static String toKebab(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                builder.append('-');
            }
            builder.append(Character.toLowerCase(ch));
        }
        return builder.toString();
    }

    public static String upperFirst(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
