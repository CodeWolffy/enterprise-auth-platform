package com.enterprise.auth.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.common.audit.AuditEvent;
import com.enterprise.auth.platform.modules.audit.application.AuditPayloadRedactor;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import com.enterprise.auth.platform.modules.audit.application.OperationLogCsvExportService;
import com.enterprise.auth.platform.modules.audit.interfaces.AuditQuery;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationLogCsvExportServiceP1Test {

    @Test
    void exportShouldRedactSensitiveDetailsAndProtectCsvFormulaFields() {
        AuditService auditService = mock(AuditService.class);
        AuditQuery query = new AuditQuery("tenant-a", "P1_EXPORT_TEST", null, null, null, 1L, 2L, 1, 100);
        when(auditService.export(any(AuditQuery.class))).thenReturn(List.of(new AuditEvent(
                "=cmd|' /C calc'!A0",
                "+operator",
                "tenant-a",
                "-request-id",
                "@127.0.0.1",
                0L,
                Map.of(
                        "password", "PlainText@123",
                        "accessToken", "token-value",
                        "authorization", "Bearer token-value",
                        "nested", Map.of("resetLink", "https://example/reset?token=raw", "safe", "kept"),
                        "formula", "=HYPERLINK(\"http://example.test\")"
                )
        )));
        OperationLogCsvExportService exportService = new OperationLogCsvExportService(
                auditService,
                new AuditPayloadRedactor()
        );

        String csv = new String(exportService.export(query), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\ufeff\"eventType\",\"operator\",\"tenantId\",\"requestId\",\"clientIp\",\"occurredAt\",\"details\"");
        assertThat(csv).contains("\"'=cmd|' /C calc'!A0\"");
        assertThat(csv).contains("\"'+operator\"");
        assertThat(csv).contains("\"'-request-id\"");
        assertThat(csv).contains("\"'@127.0.0.1\"");
        assertThat(csv).contains("password=******");
        assertThat(csv).contains("accessToken=******");
        assertThat(csv).contains("authorization=******");
        assertThat(csv).contains("resetLink=******");
        assertThat(csv).contains("safe=kept");
        assertThat(csv).doesNotContain("PlainText@123")
                .doesNotContain("token-value")
                .doesNotContain("Bearer token-value")
                .doesNotContain("https://example/reset?token=raw");
    }
}