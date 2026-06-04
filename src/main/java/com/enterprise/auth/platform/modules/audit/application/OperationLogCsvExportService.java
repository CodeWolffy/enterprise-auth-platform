package com.enterprise.auth.platform.modules.audit.application;

import com.enterprise.auth.platform.common.audit.AuditEvent;
import com.enterprise.auth.platform.modules.audit.interfaces.AuditQuery;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OperationLogCsvExportService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final List<String> HEADERS = List.of(
            "eventType",
            "operator",
            "tenantId",
            "requestId",
            "clientIp",
            "occurredAt",
            "details"
    );

    private final AuditService auditService;
    private final AuditPayloadRedactor auditPayloadRedactor;

    public OperationLogCsvExportService(AuditService auditService, AuditPayloadRedactor auditPayloadRedactor) {
        this.auditService = auditService;
        this.auditPayloadRedactor = auditPayloadRedactor;
    }

    public byte[] export(AuditQuery query) {
        List<AuditEvent> records = auditService.export(query);
        StringBuilder csv = new StringBuilder("\ufeff");
        appendRow(csv, HEADERS);
        for (AuditEvent event : records) {
            appendRow(csv, List.of(
                    safe(event.type()),
                    safe(event.operator()),
                    safe(event.tenantId()),
                    safe(event.requestId()),
                    safe(event.clientIp()),
                    event.occurredAt() == null ? "" : FORMATTER.format(Instant.ofEpochMilli(event.occurredAt())),
                    event.details() == null ? "{}" : auditPayloadRedactor.redact(event.details()).toString()
            ));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendRow(StringBuilder csv, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(escape(values.get(index)));
        }
        csv.append('\n');
    }

    private String escape(String value) {
        String protectedValue = protectFormula(value);
        return '"' + protectedValue.replace("\"", "\"\"") + '"';
    }

    private String protectFormula(String value) {
        String text = value == null ? "" : value;
        String trimmed = text.stripLeading();
        if (trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("-") || trimmed.startsWith("@")) {
            return "'" + text;
        }
        return text;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}