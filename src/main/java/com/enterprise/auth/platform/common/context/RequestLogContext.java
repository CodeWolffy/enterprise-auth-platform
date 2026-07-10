package com.enterprise.auth.platform.common.context;

import org.slf4j.MDC;

/** Maintains the structured logging fields owned by the HTTP request lifecycle. */
public final class RequestLogContext {

    private static final String REQUEST_ID = "requestId";
    private static final String CLIENT_IP = "clientIp";
    private static final String HTTP_METHOD = "httpMethod";
    private static final String REQUEST_PATH = "requestPath";
    private static final String TENANT_ID = "tenantId";
    private static final String USER_ID = "userId";
    private static final String USERNAME = "username";

    private RequestLogContext() {
    }

    public static void bindRequest(String requestId, String clientIp, String method, String path) {
        put(REQUEST_ID, requestId);
        put(CLIENT_IP, clientIp);
        put(HTTP_METHOD, method);
        put(REQUEST_PATH, path);
    }

    public static void bindPrincipal(Object userId, String username, String activeTenantId) {
        put(TENANT_ID, activeTenantId);
        put(USER_ID, userId);
        put(USERNAME, username);
    }

    public static void clearPrincipal() {
        MDC.remove(TENANT_ID);
        MDC.remove(USER_ID);
        MDC.remove(USERNAME);
    }

    public static void clearRequest() {
        clearPrincipal();
        MDC.remove(REQUEST_ID);
        MDC.remove(CLIENT_IP);
        MDC.remove(HTTP_METHOD);
        MDC.remove(REQUEST_PATH);
    }

    private static void put(String key, Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, String.valueOf(value));
    }
}
