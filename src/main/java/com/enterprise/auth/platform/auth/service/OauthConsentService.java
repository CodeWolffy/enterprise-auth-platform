package com.enterprise.auth.platform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.auth.platform.auth.dto.ConsentView;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.model.PageResult;
import com.enterprise.auth.platform.persistence.entity.SysAuditLogEntity;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.entity.SysOauthConsentEntity;
import com.enterprise.auth.platform.persistence.mapper.SysAuditLogMapper;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import com.enterprise.auth.platform.persistence.mapper.SysOauthConsentMapper;
import com.enterprise.auth.platform.tenant.TenantContext;
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
        String tenantId = currentTenantId();
        List<String> tenantClientRowIds = loadTenantClientRowIds(tenantId, clientId);
        if (tenantClientRowIds.isEmpty()) {
            return PageResult.of(0, page, size, List.of());
        }

        LambdaQueryWrapper<SysOauthConsentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysOauthConsentEntity::getRegisteredClientId, tenantClientRowIds);
        if (StringUtils.hasText(principalName)) {
            wrapper.like(SysOauthConsentEntity::getPrincipalName, principalName.trim());
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
                                .eq(SysOauthClientEntity::getTenantId, tenantId)
                                .eq(SysOauthClientEntity::getDeleted, 0))
                        .stream()
                        .collect(Collectors.toMap(
                                client -> String.valueOf(client.getId()),
                                Function.identity(),
                                (left, right) -> left
                        ));

        List<ConsentView> records = resultPage.getRecords().stream()
                .map(consent -> toView(consent, clientMap.get(consent.getRegisteredClientId()), tenantId))
                .toList();
        return PageResult.of(resultPage.getTotal(), (int) resultPage.getCurrent(), (int) resultPage.getSize(), records);
    }

    public void revokeConsent(String registeredClientId, String principalName) {
        String tenantId = currentTenantId();
        SysOauthClientEntity client = sysOauthClientMapper.selectOne(new LambdaQueryWrapper<SysOauthClientEntity>()
                .eq(SysOauthClientEntity::getId, parseClientId(registeredClientId))
                .eq(SysOauthClientEntity::getTenantId, tenantId)
                .eq(SysOauthClientEntity::getDeleted, 0)
                .last("limit 1"));
        if (client == null) {
            throw new BusinessException("ACCESS_DENIED", "No permission to revoke this consent record");
        }

        org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent consent =
                authorizationConsentService.findById(registeredClientId, principalName);
        if (consent != null) {
            authorizationConsentService.remove(consent);
        }
        sysOauthConsentMapper.delete(new LambdaQueryWrapper<SysOauthConsentEntity>()
                .eq(SysOauthConsentEntity::getRegisteredClientId, registeredClientId)
                .eq(SysOauthConsentEntity::getPrincipalName, principalName));
    }

    private List<String> loadTenantClientRowIds(String tenantId, String fuzzyClientId) {
        LambdaQueryWrapper<SysOauthClientEntity> query = new LambdaQueryWrapper<SysOauthClientEntity>()
                .eq(SysOauthClientEntity::getTenantId, tenantId)
                .eq(SysOauthClientEntity::getDeleted, 0);
        if (StringUtils.hasText(fuzzyClientId)) {
            query.like(SysOauthClientEntity::getClientId, fuzzyClientId.trim());
        }
        return sysOauthClientMapper.selectList(query)
                .stream()
                .map(SysOauthClientEntity::getId)
                .map(String::valueOf)
                .toList();
    }

    private ConsentView toView(SysOauthConsentEntity consent, SysOauthClientEntity client, String tenantId) {
        String publicClientId = client == null ? consent.getRegisteredClientId() : client.getClientId();
        String clientName = client == null ? "unknown-client" : client.getClientName();
        ConsentAuditSummary summary = summarizeAudit(consent.getRegisteredClientId(), publicClientId, consent.getPrincipalName());
        return new ConsentView(
                consent.getRegisteredClientId(),
                client == null ? tenantId : client.getTenantId(),
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
            Instant occurredAt = event.getOccurredAt() == null
                    ? null
                    : event.getOccurredAt().atZone(ZoneId.systemDefault()).toInstant();
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

    private Long parseClientId(String registeredClientId) {
        try {
            return Long.parseLong(registeredClientId);
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }

    private record ConsentAuditSummary(Instant lastGrantedAt, Instant lastRevokedAt, long auditEventCount) {
    }
}
