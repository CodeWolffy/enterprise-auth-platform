package com.enterprise.auth.platform.common.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class StringToInstantConverter implements Converter<String, Instant> {

    @Override
    public Instant convert(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        String text = source.trim();

        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // 回退到以下处理
        }

        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            // 回退到以下处理
        }

        throw new IllegalArgumentException("日期时间必须包含时区偏移，例如 2026-07-03T10:00:00+08:00 或 2026-07-03T02:00:00Z：" + source);
    }
}
