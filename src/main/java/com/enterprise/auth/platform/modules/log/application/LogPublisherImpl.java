package com.enterprise.auth.platform.modules.log.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.context.RequestContext;
import com.enterprise.auth.platform.common.web.ClientIpResolver;
import com.enterprise.auth.platform.common.web.IpLocationResolver;
import com.enterprise.auth.platform.modules.log.domain.event.LogEvent;
import com.enterprise.auth.platform.modules.log.domain.event.LoginLogEvent;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLoginLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLoginLogMapper;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class LogPublisherImpl implements LogPublisher {

    private final SysLogMapper sysLogMapper;
    private final SysLoginLogMapper sysLoginLogMapper;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;
    private final IpLocationResolver ipLocationResolver;

    public LogPublisherImpl(SysLogMapper sysLogMapper, SysLoginLogMapper sysLoginLogMapper,
                            ObjectMapper objectMapper, ClientIpResolver clientIpResolver,
                            IpLocationResolver ipLocationResolver) {
        this.sysLogMapper = sysLogMapper;
        this.sysLoginLogMapper = sysLoginLogMapper;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
        this.ipLocationResolver = ipLocationResolver;
    }

    @Override
    public void publish(LogEvent event) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        String clientIp = StringUtils.hasText(event.clientIp()) ? event.clientIp() : RequestContext.getClientIp();
        if (!StringUtils.hasText(clientIp) && request != null) {
            clientIp = clientIpResolver.resolve(request);
        }
        String location = StringUtils.hasText(event.location()) ? event.location() : ipLocationResolver.resolve(clientIp);
        String method = StringUtils.hasText(event.method()) ? event.method() : (request != null ? request.getMethod() : null);
        String requestUri = StringUtils.hasText(event.requestUri()) ? event.requestUri() : (request != null ? request.getRequestURI() : null);
        String requestParams = event.requestParams();
        if (!StringUtils.hasText(requestParams) && request != null) {
            requestParams = formatRequestParams(request);
        }
        Long requestTime = event.requestTime();
        if (requestTime == null) {
            Long startTime = RequestContext.getStartTime();
            if (startTime != null) {
                requestTime = System.currentTimeMillis() - startTime;
            }
        }

        SysLogEntity entity = new SysLogEntity();
        entity.setTenantId(StringUtils.hasText(event.tenantId()) ? event.tenantId() : "platform");
        entity.setEventType(event.type());
        entity.setOperator(event.operator());
        entity.setCreatedAt(TimeSupport.utcNowDateTime());
        entity.setCreatedBy(event.operator());
        entity.setRequestId(event.requestId());
        entity.setClientIp(clientIp);
        entity.setLocation(location);
        entity.setMethod(method);
        entity.setRequestUri(requestUri);
        entity.setRequestParams(requestParams);
        entity.setRequestTime(requestTime);
        entity.setStatus(StringUtils.hasText(event.status()) ? event.status() : "1");
        entity.setExMsg(event.exMsg());
        entity.setPayloadJson(toJson(event.details()));
        sysLogMapper.insert(entity);
    }

    @Override
    public void publish(LoginLogEvent event) {
        SysLoginLogEntity entity = new SysLoginLogEntity();
        entity.setTenantId(StringUtils.hasText(event.tenantId()) ? event.tenantId() : "platform");
        entity.setUserName(event.operator());
        entity.setStatus(event.status());
        entity.setMsg(event.msg());
        entity.setIpAddr(event.ipAddr());
        entity.setLocation(event.location());
        entity.setBrowser(event.browser());
        entity.setOs(event.os());
        entity.setCreatedBy(event.operator());
        entity.setCreatedAt(TimeSupport.utcNowDateTime());
        sysLoginLogMapper.insert(entity);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String formatRequestParams(HttpServletRequest request) {
        String query = request.getQueryString();
        if (StringUtils.hasText(query)) {
            return query;
        }
        if (request.getParameterMap().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(request.getParameterMap());
        } catch (Exception ex) {
            return null;
        }
    }
}
