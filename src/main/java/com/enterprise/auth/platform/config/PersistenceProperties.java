package com.enterprise.auth.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.persistence")
// 统一收敛持久化开关，避免数据库模式条件判断分散在各业务类中。
public record PersistenceProperties(boolean databaseEnabled) {
}
