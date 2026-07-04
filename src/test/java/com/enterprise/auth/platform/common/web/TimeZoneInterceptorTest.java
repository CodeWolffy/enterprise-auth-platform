package com.enterprise.auth.platform.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.enterprise.auth.platform.common.context.TimeZoneContext;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TimeZoneInterceptorTest {

    private final TimeZoneInterceptor interceptor = new TimeZoneInterceptor();

    @AfterEach
    void tearDown() {
        TimeZoneContext.clear();
    }

    @Test
    void acceptsIanaTimezoneAndExposesItInResponseHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TimeZoneInterceptor.HEADER_NAME, "America/New_York");

        interceptor.preHandle(request, response, new Object());

        assertEquals(ZoneId.of("America/New_York"), TimeZoneContext.getZone());
        assertEquals("America/New_York", response.getHeader(TimeZoneInterceptor.HEADER_NAME));
    }

    @Test
    void fallsBackToDefaultBusinessTimezoneWhenHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertEquals(ZoneId.of("Asia/Shanghai"), TimeZoneContext.getZone());
        assertEquals("Asia/Shanghai", response.getHeader(TimeZoneInterceptor.HEADER_NAME));
    }

    @Test
    void rejectsInvalidTimezone() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TimeZoneInterceptor.HEADER_NAME, "Mars/Base");

        assertThrows(
                IllegalArgumentException.class,
                () -> interceptor.preHandle(request, response, new Object())
        );
    }
}
