package com.enterprise.auth.platform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.auth.platform.auth.dto.ConsentView;
import com.enterprise.auth.platform.common.model.PageResult;
import com.enterprise.auth.platform.persistence.entity.SysAuditLogEntity;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.entity.SysOauthConsentEntity;
import com.enterprise.auth.platform.persistence.mapper.SysAuditLogMapper;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import com.enterprise.auth.platform.persistence.mapper.SysOauthConsentMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OauthConsentService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final SysOauthConsentMapper sysOauthConsentMapper;
    private final SysOauthClientMapper sysOauthClientMapper;
    private final OAuth2AuthorizationConsentService authorizationConsentService;
    private final SysAuditLogMapper sysAuditLogMapper;
    private final ObjectMapper objectMapper;

    public OauthConsentService(
            SysOauthConsentMapper sysOauthConsentMapper,
            SysOauthClientMapper sysOauthClientMapper,
            OAuth2AuthorizationConsentService authorizationConsentService,
            SysAuditLogMapper sysAuditLogMapper,
            ObjectMapper objectMapper
    ) {
        this.sysOauthConsentMapper = sysOauthConsentMapper;
        this.sysOauthClientMapper = sysOauthClientMapper;
        this.authorizationConsentService = authorizationConsentService;
        this.sysAuditLogMapper = sysAuditLogMapper;
        this.objectMapper = objectMapper;
    }

    public PageResult<ConsentView> queryConsents(int page, int size, String clientId, String principalName) {
        LambdaQueryWrapper<SysOauthConsentEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(principalName)) {
            wrapper.like(SysOauthConsentEntity::getPrincipalName, principalName.trim());
        }
        if (StringUtils.hasText(clientId)) {
            List<String> matchedClientIds = sysOauthClientMapper.selectList(new LambdaQueryWrapper<SysOauthClientEntity>()
                            .eq(SysOauthClientEntity::getDeleted, 0)
                            .like(SysOauthClientEntity::getClientId, clientId.trim()))
                    .stream()
                    .map(SysOauthClientEntity::getId)
                    .map(String::valueOf)
                    .toList();
            if (matchedClientIds.isEmpty()) {
                return PageResult.of(0, page, size, List.of());
            }
            wrapper.in(SysOauthConsentEntity::getRegisteredClientId, matchedClientIds);
        }

        Page<SysOauthConsentEntity> resultPage = sysOauthConsentMapper.selectPage(
                new Page<>(page, size),
                wrapper.orderByAsc(SysOauthConsentEntity::getPrincipalName)
                        .orderByAsc(SysOauthConsentEntity::getRegisteredClientId)
        );

        List<String> registeredClientIds = resultPage.getRecords().stream()
                .map(SysOauthConsentEntity::getRegisteredClientId)
                .distinct()
                .toList();
        Map<String, SysOauthClientEntity> clientMap = registeredClientIds.isEmpty()
                ? Map.of()
                : sysOauthClientMapper.selectList(new LambdaQueryWrapper<SysOauthClientEntity>()
                                .in(SysOauthClientEntity::getId, registeredClientIds)
                                .eq(SysOauthClientEntity::getDeleted, 0))
                        .stream()
                        .collect(Collectors.toMap(
                                client -> String.valueOf(client.getId()),
                                Function.identity(),
                                (left, right) -> left
                        ));

        List<ConsentView> records = resultPage.getRecords().stream()
                .map(consent -> toView(consent, clientMap.get(consent.getRegisteredClientId())))
                .toList();
        return PageResult.of(resultPage.getTotal(), (int) resultPage.getCurrent(), (int) resultPage.getSize(), records);
    }

    public void revokeConsent(String registeredClientId, String principalName) {
        org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent consent =
                authorizationConsentService.findById(registeredClientId, principalName);
        if (consent != null) {
            authorizationConsentService.remove(consent);
        }
        sysOauthConsentMapper.delete(new LambdaQueryWrapper<SysOauthConsentEntity>()
                .eq(SysOauthConsentEntity::getRegisteredClientId, registeredClientId)
                .eq(SysOauthConsentEntity::getPrincipalName, principalName));
    }

    private ConsentView toView(SysOauthConsentEntity consent, SysOauthClientEntity client) {
        String publicClientId = client == null ? consent.getRegisteredClientId() : client.getClientId();
        String clientName = client == null ? "未知客户端" : client.getClientName();
        ConsentAuditSummary summary = summarizeAudit(consent.getRegisteredClientId(), publicClientId, consent.getPrincipalName());
        return new ConsentView(
                consent.getRegisteredClientId(),
                client == null ? "platform" : client.getTenantId(),
                publicClientId,
                clientName,
                consent.getPrincipalName(),
                splitAuthorities(consent.getAuthorities()),
                summary.lastGrantedAt(),
                summary.lastRevokedAt(),
                summary.auditEventCount()
        );
    }

    private List<String> splitAuthorities(String authorities) {
        if (!StringUtils.hasText(authorities)) {
            return List.of();
        }
        return List.of(authorities.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private ConsentAuditSummary summarizeAudit(String registeredClientId, String clientId, String principalName) {
        List<SysAuditLogEntity> events = sysAuditLogMapper.selectList(new LambdaQueryWrapper<SysAuditLogEntity>()
                .in(SysAuditLogEntity::getEventType, List.of("OAUTH_CONSENT_GRANTED", "OAUTH_CONSENT_REVOKED"))
                .eq(SysAuditLogEntity::getOperator, principalName)
                .orderByDesc(SysAuditLogEntity::getOccurredAt));
        Instant lastGrantedAt = null;
        Instant lastRevokedAt = null;
        long count = 0;
        for (SysAuditLogEntity event : events) {
            Map<String, Object> payload = parsePayload(event.getPayloadJson());
            String payloadRegisteredClientId = stringValue(payload.get("registeredClientId"));
            String payloadClientId = stringValue(payload.get("clientId"));
            if (!registeredClientId.equals(payloadRegisteredClientId) && !clientId.equals(payloadClientId)) {
                continue;
            }
            count++;
            Instant occurredAt = event.getOccurredAt() == null ? null : event.getOccurredAt().atZone(ZoneId.systemDefault()).toInstant();
            if ("OAUTH_CONSENT_GRANTED".equals(event.getEventType()) && lastGrantedAt == null) {
                lastGrantedAt = occurredAt;
            }
            if ("OAUTH_CONSENT_REVOKED".equals(event.getEventType()) && lastRevokedAt == null) {
                lastRevokedAt = occurredAt;
            }
        }
        return new ConsentAuditSummary(lastGrantedAt, lastRevokedAt, count);
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record ConsentAuditSummary(Instant lastGrantedAt, Instant lastRevokedAt, long auditEventCount) {
    }
}
