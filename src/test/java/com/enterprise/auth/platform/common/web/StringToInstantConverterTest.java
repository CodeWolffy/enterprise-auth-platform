package com.enterprise.auth.platform.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StringToInstantConverterTest {

    private final TimeZone originalTimeZone = TimeZone.getDefault();
    private final StringToInstantConverter converter = new StringToInstantConverter();

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    void parsesTimezoneLessDateTimeAsUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        Instant instant = converter.convert("2026-06-27 12:00:00");

        assertEquals(Instant.parse("2026-06-27T12:00:00Z"), instant);
    }
}
