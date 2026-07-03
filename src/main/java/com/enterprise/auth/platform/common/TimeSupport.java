package com.enterprise.auth.platform.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.springframework.util.StringUtils;

public final class TimeSupport {

    public static final ZoneId UTC = ZoneOffset.UTC;
    public static final ZoneId DEFAULT_BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private TimeSupport() {
    }

    public static Instant now() {
        return Instant.now();
    }

    public static LocalDate today(ZoneId zone) {
        return LocalDate.now(zone == null ? UTC : zone);
    }

    public static Instant fromEpochMilli(Long epochMs) {
        return epochMs == null ? null : Instant.ofEpochMilli(epochMs);
    }

    public static Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ex) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeParseException nested) {
                throw new IllegalArgumentException("日期时间必须包含时区偏移，例如 2026-07-03T10:15:30+08:00 或 2026-07-03T02:15:30Z：" + value, nested);
            }
        }
    }

    public static Long toEpochMilli(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    public static ZoneId zone(String zoneId) {
        if (!StringUtils.hasText(zoneId)) {
            return DEFAULT_BUSINESS_ZONE;
        }
        return ZoneId.of(zoneId.trim());
    }

    public static Instant startOfDay(LocalDate date, ZoneId zone) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay(zone == null ? DEFAULT_BUSINESS_ZONE : zone).toInstant();
    }

    public static LocalDate toLocalDate(Instant instant, ZoneId zone) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(zone == null ? DEFAULT_BUSINESS_ZONE : zone).toLocalDate();
    }
}
