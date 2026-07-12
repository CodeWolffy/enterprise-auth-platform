package com.enterprise.auth.platform.modules.log.application;

import com.enterprise.auth.platform.modules.auth.application.SecuritySupport;
import com.enterprise.auth.platform.common.context.RequestContext;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.web.IpLocationResolver;
import com.enterprise.auth.platform.modules.log.domain.event.LogEvent;
import com.enterprise.auth.platform.modules.log.infrastructure.annotation.SysLog;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
public class LogAopAspect {

    private final ApplicationEventPublisher eventPublisher;
    private final IpLocationResolver ipLocationResolver;

    public LogAopAspect(ApplicationEventPublisher eventPublisher, IpLocationResolver ipLocationResolver) {
        this.eventPublisher = eventPublisher;
        this.ipLocationResolver = ipLocationResolver;
    }

    @Around("@annotation(sysLog)")
    public Object around(ProceedingJoinPoint point, SysLog sysLog) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        String eventType = sysLog.value();
        String operator = SecuritySupport.currentOperator();
        String tenantId = TenantContext.getTenantId();
        String requestId = RequestContext.getRequestId();
        String clientIp = request != null ? RequestContext.getClientIp() : null;
        String location = ipLocationResolver.resolve(clientIp);
        String method = request != null ? request.getMethod() : null;
        String requestUri = request != null ? request.getRequestURI() : null;
        String requestParams = request != null ? request.getQueryString() : null;

        long start = System.currentTimeMillis();
        try {
            Object result = point.proceed();
            long cost = System.currentTimeMillis() - start;
            eventPublisher.publishEvent(new LogEvent(
                    eventType,
                    operator,
                    tenantId,
                    details(requestId, clientIp, method, requestUri, requestParams, cost, null),
                    requestId,
                    clientIp,
                    location,
                    method,
                    requestUri,
                    requestParams,
                    cost,
                    "1",
                    null
            ));
            return result;
        } catch (Throwable ex) {
            long cost = System.currentTimeMillis() - start;
            String message = ex.getMessage();
            eventPublisher.publishEvent(new LogEvent(
                    eventType,
                    operator,
                    tenantId,
                    details(requestId, clientIp, method, requestUri, requestParams, cost, message),
                    requestId,
                    clientIp,
                    location,
                    method,
                    requestUri,
                    requestParams,
                    cost,
                    "0",
                    message
            ));
            throw ex;
        }
    }

    private Map<String, Object> details(String requestId, String clientIp, String method, String requestUri,
                                        String requestParams, long requestTime, String exMsg) {
        Map<String, Object> details = new LinkedHashMap<>();
        putIfPresent(details, "requestId", requestId);
        putIfPresent(details, "clientIp", clientIp);
        putIfPresent(details, "method", method);
        putIfPresent(details, "requestUri", requestUri);
        putIfPresent(details, "requestParams", requestParams);
        details.put("requestTime", requestTime);
        putIfPresent(details, "exMsg", exMsg);
        return details;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
