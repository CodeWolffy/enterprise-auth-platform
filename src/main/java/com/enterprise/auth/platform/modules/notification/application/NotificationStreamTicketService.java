package com.enterprise.auth.platform.modules.notification.application;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NotificationStreamTicketService {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamTicketService.class);
    private static final Duration TICKET_TTL = Duration.ofSeconds(60);
    private static final int TICKET_BYTES = 32;
    private static final int MAX_TICKET_ATTEMPTS = 3;
    private static final String STREAM_TICKET_PREFIX = "notification:stream-ticket:";
    private static final DefaultRedisScript<String> CONSUME_TICKET_SCRIPT = new DefaultRedisScript<>(
            """
            local payload = redis.call('GET', KEYS[1])
            if payload then
                redis.call('DEL', KEYS[1])
            end
            return payload
            """,
            String.class
    );

    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock = Clock.systemUTC();
    private final Map<String, StreamTicket> tickets = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public NotificationStreamTicketService(
            StringRedisTemplate redisTemplate,
            SecurityProperties securityProperties,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    public StreamTicketResponse issue() {
        String token = StpUtil.getTokenValue();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException("UNAUTHORIZED", "缺少站内通知订阅凭证");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        SaSession tokenSession = StpUtil.getTokenSession();
        String tenantId = resolveTenantId(tokenSession);
        long expiresAt = clock.millis() + TICKET_TTL.toMillis();
        for (int attempt = 0; attempt < MAX_TICKET_ATTEMPTS; attempt++) {
            String ticketValue = newTicketValue();
            StreamTicket ticket = new StreamTicket(ticketValue, token, tenantId, userId, expiresAt);
            if (store(ticket)) {
                return new StreamTicketResponse(ticketValue, expiresAt);
            }
        }
        throw new BusinessException(
                "NOTIFICATION_STREAM_TICKET_ERROR",
                "站内通知订阅凭证创建失败",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    public StreamTicket consume(String ticketValue) {
        if (!StringUtils.hasText(ticketValue)) {
            throw new BusinessException("UNAUTHORIZED", "缺少站内通知订阅凭证");
        }
        StreamTicket ticket = consumeStoredTicket(ticketValue.trim());
        if (ticket == null || ticket.expiresAt() < clock.millis()) {
            throw new BusinessException("UNAUTHORIZED", "站内通知订阅凭证已失效");
        }
        Object loginId;
        try {
            loginId = StpUtil.stpLogic.getLoginIdByToken(ticket.token());
        } catch (Exception ex) {
            log.debug("站内通知订阅凭证会话校验失败。userId={}，error={}",
                    ticket.userId(), ex.getMessage());
            loginId = null;
        }
        if (loginId == null || !String.valueOf(ticket.userId()).equals(String.valueOf(loginId))) {
            throw new BusinessException("UNAUTHORIZED", "站内通知订阅凭证已失效");
        }
        return ticket;
    }

    private String resolveTenantId(SaSession tokenSession) {
        Object activeTenantId = tokenSession.get("activeTenantId");
        if (activeTenantId != null && StringUtils.hasText(String.valueOf(activeTenantId))) {
            return String.valueOf(activeTenantId);
        }
        Object tenantId = tokenSession.get("tenantId");
        if (tenantId != null && StringUtils.hasText(String.valueOf(tenantId))) {
            return String.valueOf(tenantId);
        }
        return "platform";
    }

    private String newTicketValue() {
        byte[] bytes = new byte[TICKET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean store(StreamTicket ticket) {
        if (useRedisTickets()) {
            return storeRedis(ticket);
        }
        tickets.put(ticket.ticket(), ticket);
        cleanupExpired();
        return true;
    }

    private StreamTicket consumeStoredTicket(String ticketValue) {
        if (useRedisTickets()) {
            return consumeRedis(ticketValue);
        }
        return tickets.remove(ticketValue);
    }

    private boolean storeRedis(StreamTicket ticket) {
        try {
            Boolean stored = redisTemplate.opsForValue()
                    .setIfAbsent(ticketKey(ticket.ticket()), objectMapper.writeValueAsString(ticket), TICKET_TTL);
            return Boolean.TRUE.equals(stored);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("站内通知订阅凭证写入 Redis 失败。userId={}，error={}",
                    ticket.userId(), ex.getMessage());
            throw new BusinessException(
                    "NOTIFICATION_STREAM_TICKET_ERROR",
                    "站内通知订阅凭证创建失败",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private StreamTicket consumeRedis(String ticketValue) {
        try {
            String payload = redisTemplate.execute(CONSUME_TICKET_SCRIPT, List.of(ticketKey(ticketValue)));
            if (!StringUtils.hasText(payload)) {
                return null;
            }
            return objectMapper.readValue(payload, StreamTicket.class);
        } catch (JsonProcessingException ex) {
            log.warn("Redis 中的站内通知订阅凭证载荷无效。error={}", ex.getMessage());
            return null;
        } catch (RuntimeException ex) {
            log.warn("站内通知订阅凭证从 Redis 消费失败。error={}", ex.getMessage());
            throw new BusinessException(
                    "NOTIFICATION_STREAM_TICKET_ERROR",
                    "站内通知订阅凭证校验失败",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private boolean useRedisTickets() {
        return securityProperties.resolvedRedis().sessionEnabled();
    }

    private String ticketKey(String ticketValue) {
        return securityProperties.resolvedRedis().resolvedNamespacePrefix() + STREAM_TICKET_PREFIX + ticketValue;
    }

    private void cleanupExpired() {
        long now = clock.millis();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    public record StreamTicket(String ticket, String token, String tenantId, Long userId, long expiresAt) {
    }

    public record StreamTicketResponse(String ticket, long expiresAt) {
    }
}
