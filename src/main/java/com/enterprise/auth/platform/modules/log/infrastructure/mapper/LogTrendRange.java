package com.enterprise.auth.platform.modules.log.infrastructure.mapper;

import java.time.Instant;

public record LogTrendRange(String dayKey, Instant fromInclusive, Instant toExclusive) {
}
