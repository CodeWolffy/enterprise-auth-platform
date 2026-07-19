package com.enterprise.auth.platform.common.context;

@FunctionalInterface
public interface RequestContextCleaner {
    void clear();
}
