package com.enterprise.auth.platform.common.context;

public final class RequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CLIENT_IP = new ThreadLocal<>();
    private static final ThreadLocal<Long> START_TIME = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static String getRequestId() {
        return REQUEST_ID.get();
    }

    public static void setClientIp(String clientIp) {
        CLIENT_IP.set(clientIp);
    }

    public static String getClientIp() {
        return CLIENT_IP.get();
    }

    public static void setStartTime(Long startTime) {
        START_TIME.set(startTime);
    }

    public static Long getStartTime() {
        return START_TIME.get();
    }

    public static void clear() {
        REQUEST_ID.remove();
        CLIENT_IP.remove();
        START_TIME.remove();
    }
}
