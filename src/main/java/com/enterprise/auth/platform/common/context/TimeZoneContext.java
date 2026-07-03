package com.enterprise.auth.platform.common.context;

import com.enterprise.auth.platform.common.TimeSupport;
import java.time.ZoneId;

public final class TimeZoneContext {

    private static final ThreadLocal<ZoneId> CURRENT_ZONE = new ThreadLocal<>();

    private TimeZoneContext() {
    }

    public static ZoneId getZone() {
        ZoneId zone = CURRENT_ZONE.get();
        return zone == null ? TimeSupport.DEFAULT_BUSINESS_ZONE : zone;
    }

    public static void setZone(ZoneId zone) {
        CURRENT_ZONE.set(zone == null ? TimeSupport.DEFAULT_BUSINESS_ZONE : zone);
    }

    public static void clear() {
        CURRENT_ZONE.remove();
    }
}
