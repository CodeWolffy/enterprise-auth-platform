package com.enterprise.auth.platform.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class TimeSupport {

    public static final ZoneId UTC = ZoneOffset.UTC;

    private TimeSupport() {
    }

    public static LocalDateTime utcNowDateTime() {
        return LocalDateTime.now(UTC);
    }

    public static LocalDate utcToday() {
        return LocalDate.now(UTC);
    }

    public static Instant fromEpochMilli(Long epochMs) {
        return epochMs == null ? null : Instant.ofEpochMilli(epochMs);
    }

    public static LocalDateTime localDateTimeFromEpochMilli(Long epochMs) {
        return toUtcDateTime(fromEpochMilli(epochMs));
    }

    public static LocalDateTime toUtcDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, UTC);
    }

    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public static Long toEpochMilli(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    public static Long toEpochMilli(LocalDateTime value) {
        Instant instant = toInstant(value);
        return instant == null ? null : instant.toEpochMilli();
    }
}
