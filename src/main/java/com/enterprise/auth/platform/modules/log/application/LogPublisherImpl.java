package com.enterprise.auth.platform.modules.log.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.context.CurrentOperatorSupplier;
import com.enterprise.auth.platform.common.context.RequestContext;
import com.enterprise.auth.platform.common.context.TenantContext;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class LogPublisherImpl implements LogPublisher {

    private static final String MASK = "******";
    private static final int MAX_REQUEST_PARAMS_LENGTH = 4096;
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "password", "passwd", "pwd", "token", "secret", "credential",
            "authorization", "authheader", "privatekey", "accesskey", "apikey",
            "resetlink", "reseturl", "sessionid", "session"
    );

    private final SysLogMapper sysLogMapper;
    private final SysLoginLogMapper sysLoginLogMapper;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;
    private final IpLocationResolver ipLocationResolver;
    private final CurrentOperatorSupplier currentOperatorSupplier;

    public LogPublisherImpl(SysLogMapper sysLogMapper, SysLoginLogMapper sysLoginLogMapper,
                            ObjectMapper objectMapper, ClientIpResolver clientIpResolver,
                            IpLocationResolver ipLocationResolver,
                            CurrentOperatorSupplier currentOperatorSupplier) {
        this.sysLogMapper = sysLogMapper;
        this.sysLoginLogMapper = sysLoginLogMapper;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
        this.ipLocationResolver = ipLocationResolver;
        this.currentOperatorSupplier = currentOperatorSupplier;
    }

    @Override
    public void publish(LogEvent event) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        String tenantId = StringUtils.hasText(event.tenantId()) ? event.tenantId() : "platform";
        String requestId = StringUtils.hasText(event.requestId()) ? event.requestId() : RequestContext.getRequestId();
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
        requestParams = sanitizeRequestParams(requestParams);
        Long requestTime = event.requestTime();
        if (requestTime == null) {
            Long startTime = RequestContext.getStartTime();
            if (startTime != null) {
                requestTime = System.currentTimeMillis() - startTime;
            }
        }

        SysLogEntity entity = new SysLogEntity();
        entity.setTenantId(tenantId);
        entity.setEventType(event.type());
        entity.setOperator(event.operator());
        entity.setCreatedAt(TimeSupport.now());
        entity.setCreatedBy(event.operator());
        entity.setRequestId(requestId);
        entity.setClientIp(clientIp);
        entity.setLocation(location);
        entity.setMethod(method);
        entity.setRequestUri(requestUri);
        entity.setRequestParams(requestParams);
        entity.setRequestTime(requestTime);
        entity.setStatus(StringUtils.hasText(event.status()) ? event.status() : "1");
        entity.setExMsg(event.exMsg());
        Map<String, Object> details = new LinkedHashMap<>(event.details());
        if (details.containsKey("requestParams")) {
            details.put("requestParams", requestParams);
        }
        entity.setPayloadJson(toJson(redact(enrichDetails(details, event.operator(), tenantId, requestId, clientIp))));
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
        entity.setCreatedAt(TimeSupport.now());
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

    private Map<String, Object> enrichDetails(
            Map<String, Object> details,
            String operator,
            String tenantId,
            String requestId,
            String clientIp
    ) {
        Map<String, Object> enriched = new LinkedHashMap<>();
        if (details != null) {
            enriched.putAll(details);
        }
        String activeTenantId = StringUtils.hasText(TenantContext.getTenantId())
                ? TenantContext.getTenantId()
                : tenantId;
        String operatorTenantId = currentOperatorSupplier.operatorTenantId()
                .filter(StringUtils::hasText)
                .orElse(activeTenantId);
        putIfAbsent(enriched, "requestId", requestId);
        putIfAbsent(enriched, "clientIp", clientIp);
        putIfAbsent(enriched, "operator", operator);
        putIfAbsent(enriched, "activeTenantId", activeTenantId);
        putIfAbsent(enriched, "operatorTenantId", operatorTenantId);
        putIfAbsent(enriched, "targetTenantId", tenantId);
        return enriched;
    }

    private Map<String, Object> redact(Map<String, Object> payload) {
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
            return collection.stream().map(item -> redactValue(key, item)).toList();
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

    private void putIfAbsent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.putIfAbsent(key, value);
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

    private String sanitizeRequestParams(String requestParams) {
        if (!StringUtils.hasText(requestParams)) {
            return null;
        }
        String[] pairs = requestParams.split("&", -1);
        StringBuilder sanitized = new StringBuilder(Math.min(requestParams.length(), MAX_REQUEST_PARAMS_LENGTH));
        for (String pair : pairs) {
            if (sanitized.length() > 0) {
                sanitized.append('&');
            }
            int separator = pair.indexOf('=');
            String rawKey = separator >= 0 ? pair.substring(0, separator) : pair;
            String decodedKey;
            try {
                decodedKey = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                decodedKey = rawKey;
            }
            if (isSensitiveKey(decodedKey)) {
                sanitized.append(rawKey).append('=').append(MASK);
            } else {
                sanitized.append(pair);
            }
            if (sanitized.length() >= MAX_REQUEST_PARAMS_LENGTH) {
                return sanitized.substring(0, MAX_REQUEST_PARAMS_LENGTH);
            }
        }
        return sanitized.toString();
    }
}
