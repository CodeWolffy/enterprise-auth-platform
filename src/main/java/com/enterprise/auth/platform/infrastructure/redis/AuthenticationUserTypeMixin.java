package com.enterprise.auth.platform.infrastructure.redis;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Jackson Mixin，用于在 Redis 缓存序列化时为 {@link
 * com.enterprise.auth.platform.modules.user.application.AuthenticationUser}
 * 附加 @class 类型信息，避免反序列化时丢失具体类型而变成 LinkedHashMap。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public interface AuthenticationUserTypeMixin {
}