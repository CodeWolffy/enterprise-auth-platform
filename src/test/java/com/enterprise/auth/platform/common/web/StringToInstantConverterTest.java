package com.enterprise.auth.platform.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class StringToInstantConverterTest {

    private final StringToInstantConverter converter = new StringToInstantConverter();

    @Test
    void parsesUtcInstant() {
        Instant instant = converter.convert("2026-06-27T12:00:00Z");

        assertEquals(Instant.parse("2026-06-27T12:00:00Z"), instant);
    }

    @Test
    void parsesOffsetDateTime() {
        Instant instant = converter.convert("2026-06-27T20:00:00+08:00");

        assertEquals(Instant.parse("2026-06-27T12:00:00Z"), instant);
    }

    @Test
    void rejectsTimezoneLessDateTime() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert("2026-06-27 12:00:00"));
    }
}
