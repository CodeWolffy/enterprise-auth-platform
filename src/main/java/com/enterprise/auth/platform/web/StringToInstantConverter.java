package com.enterprise.auth.platform.web;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

        String normalized = text.replace(' ', 'T');
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(normalized);
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("无效的日期时间格式：" + source, ex);
        }
    }
}

