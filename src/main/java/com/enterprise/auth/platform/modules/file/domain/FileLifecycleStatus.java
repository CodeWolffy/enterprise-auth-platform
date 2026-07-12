package com.enterprise.auth.platform.modules.file.domain;

/**
 * 文件存储生命周期：短事务建单 → 事务外上传/删除 → 短事务确认。
 */
public enum FileLifecycleStatus {
    PENDING,
    READY,
    FAILED,
    DELETE_PENDING;

    public static FileLifecycleStatus from(String value) {
        if (value == null || value.isBlank()) {
            return READY;
        }
        try {
            return FileLifecycleStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return READY;
        }
    }
}