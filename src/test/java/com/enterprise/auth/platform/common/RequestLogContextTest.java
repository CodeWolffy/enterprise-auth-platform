package com.enterprise.auth.platform.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.auth.platform.common.context.RequestLogContext;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class RequestLogContextTest {

    @AfterEach
    void clear() {
        RequestLogContext.clearRequest();
    }

    @Test
    void shouldBindAndClearRequestAndPrincipalFields() {
        RequestLogContext.bindRequest("request-1", "127.0.0.1", "GET", "/api/test");
        RequestLogContext.bindPrincipal(7L, "alice", "tenant-a");

        assertThat(MDC.getCopyOfContextMap()).containsAllEntriesOf(Map.of(
                "requestId", "request-1",
                "clientIp", "127.0.0.1",
                "httpMethod", "GET",
                "requestPath", "/api/test",
                "tenantId", "tenant-a",
                "userId", "7",
                "username", "alice"
        ));

        RequestLogContext.clearPrincipal();
        assertThat(MDC.get("tenantId")).isNull();
        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("username")).isNull();
        assertThat(MDC.get("requestId")).isEqualTo("request-1");

        RequestLogContext.clearRequest();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
