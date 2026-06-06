package com.enterprise.auth.platform.modules.audit.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AuditPayloadRedactor {

    private static final String MASK = "******";
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "password",
            "passwd",
            "pwd",
            "token",
            "secret",
            "credential",
            "authorization",
            "authheader",
            "privatekey",
            "accesskey",
            "apikey",
            "resetlink",
            "reseturl",
            "sessionid",
            "session"
    );

    public Map<String, Object> redact(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> redacted = new LinkedHashMap<>();
        payload.forEach((key, value) -> redacted.put(key, redactValue(key, value)));
        return redacted;
    }

    private Object redactValue(String key, Object value) {
        if (isSensitiveKey(key)) {
            return MASK;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> nested.put(
                    String.valueOf(nestedKey),
                    redactValue(String.valueOf(nestedKey), nestedValue)
            ));
            return nested;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> redactValue(key, item))
                    .toList();
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYWORDS.stream().anyMatch(normalized::contains);
    }
}