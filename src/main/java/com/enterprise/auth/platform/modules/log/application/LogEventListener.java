package com.enterprise.auth.platform.modules.log.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.log.domain.event.LogEvent;
import com.enterprise.auth.platform.modules.log.domain.event.LoginLogEvent;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLoginLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLoginLogMapper;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LogEventListener {

    private final SysLogMapper sysLogMapper;
    private final SysLoginLogMapper sysLoginLogMapper;
    private final ObjectMapper objectMapper;

    public LogEventListener(SysLogMapper sysLogMapper, SysLoginLogMapper sysLoginLogMapper, ObjectMapper objectMapper) {
        this.sysLogMapper = sysLogMapper;
        this.sysLoginLogMapper = sysLoginLogMapper;
        this.objectMapper = objectMapper;
    }

    @Async
    @EventListener
    public void handleLogEvent(LogEvent event) {
        SysLogEntity entity = new SysLogEntity();
        entity.setTenantId(StringUtils.hasText(event.tenantId()) ? event.tenantId() : "platform");
        entity.setEventType(event.type());
        entity.setOperator(event.operator());
        entity.setCreatedAt(TimeSupport.utcNowDateTime());
        entity.setCreatedBy(event.operator());
        entity.setRequestId(event.requestId());
        entity.setClientIp(event.clientIp());
        entity.setLocation(event.location());
        entity.setMethod(event.method());
        entity.setRequestUri(event.requestUri());
        entity.setRequestParams(event.requestParams());
        entity.setRequestTime(event.requestTime());
        entity.setStatus(StringUtils.hasText(event.status()) ? event.status() : "1");
        entity.setExMsg(event.exMsg());
        entity.setPayloadJson(toJson(event.details()));
        sysLogMapper.insert(entity);
    }

    @Async
    @EventListener
    public void handleLoginLogEvent(LoginLogEvent event) {
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
}