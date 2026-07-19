package com.enterprise.auth.platform.common.web;

import com.enterprise.auth.platform.common.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class RequestIdSupport {

    public static final String HEADER = "X-Request-Id";
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private RequestIdSupport() {
    }

    public static String resolveIncoming(String candidate) {
        if (StringUtils.hasText(candidate)) {
            String normalized = candidate.trim();
            if (VALID_REQUEST_ID.matcher(normalized).matches()) {
                return normalized;
            }
        }
        return UUID.randomUUID().toString();
    }

    public static String currentOrCreate(HttpServletRequest request) {
        String current = RequestContext.getRequestId();
        if (StringUtils.hasText(current)) {
            return current;
        }
        if (request != null) {
            Object attribute = request.getAttribute(HEADER);
            if (attribute != null && StringUtils.hasText(String.valueOf(attribute))) {
                return String.valueOf(attribute);
            }
            return resolveIncoming(request.getHeader(HEADER));
        }
        return UUID.randomUUID().toString();
    }
}
