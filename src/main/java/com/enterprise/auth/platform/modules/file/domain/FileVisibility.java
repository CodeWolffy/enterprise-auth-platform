package com.enterprise.auth.platform.modules.file.domain;

public enum FileVisibility {
    PUBLIC,
    TENANT,
    OWNER,
    PRIVATE;

    public static FileVisibility from(String value) {
        if (value == null || value.isBlank()) {
            return OWNER;
        }
        try {
            return FileVisibility.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return OWNER;
        }
    }
}