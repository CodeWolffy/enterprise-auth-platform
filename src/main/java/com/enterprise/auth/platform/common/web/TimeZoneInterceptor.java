package com.enterprise.auth.platform.common.web;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.context.TimeZoneContext;
import com.enterprise.auth.platform.common.exception.InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.DateTimeException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TimeZoneInterceptor implements HandlerInterceptor {

    public static final String HEADER_NAME = "X-Time-Zone";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String rawTimeZone = request.getHeader(HEADER_NAME);
        try {
            TimeZoneContext.setZone(TimeSupport.zone(rawTimeZone));
        } catch (DateTimeException ex) {
            throw new InvalidRequestException("无效时区，请使用 IANA 时区名称，例如 Asia/Shanghai：" + rawTimeZone, ex);
        }
        response.setHeader(HEADER_NAME, TimeZoneContext.getZone().getId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TimeZoneContext.clear();
    }
}
