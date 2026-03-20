package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import com.enterprise.auth.platform.tenant.TenantContext;
import java.util.Map;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.StringUtils;

public class AuditingAuthorizationConsentService implements OAuth2AuthorizationConsentService {

    private final OAuth2AuthorizationConsentService delegate;
    private final RegisteredClientRepository registeredClientRepository;
    private final SysOauthClientMapper sysOauthClientMapper;
    private final AuditService auditService;

    public AuditingAuthorizationConsentService(
            OAuth2AuthorizationConsentService delegate,
            RegisteredClientRepository registeredClientRepository,
            SysOauthClientMapper sysOauthClientMapper,
            AuditService auditService
    ) {
        this.delegate = delegate;
        this.registeredClientRepository = registeredClientRepository;
        this.sysOauthClientMapper = sysOauthClientMapper;
        this.auditService = auditService;
    }

    @Override
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        delegate.save(authorizationConsent);
        RegisteredClient registeredClient = registeredClientRepository.findById(authorizationConsent.getRegisteredClientId());
        String publicClientId = registeredClient == null ? authorizationConsent.getRegisteredClientId() : registeredClient.getClientId();
        String clientName = resolveClientName(authorizationConsent.getRegisteredClientId(), publicClientId);
        String tenantId = resolveTenantId(registeredClient, authorizationConsent.getPrincipalName());
        auditService.record("OAUTH_CONSENT_GRANTED", authorizationConsent.getPrincipalName(), tenantId, Map.of(
                "registeredClientId", authorizationConsent.getRegisteredClientId(),
                "clientId", publicClientId,
                "clientName", clientName,
                "principalName", authorizationConsent.getPrincipalName(),
                "authorities", authorizationConsent.getAuthorities().stream().map(granted -> granted.getAuthority()).toList()
        ));
    }

    @Override
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        delegate.remove(authorizationConsent);
        RegisteredClient registeredClient = registeredClientRepository.findById(authorizationConsent.getRegisteredClientId());
        String publicClientId = registeredClient == null ? authorizationConsent.getRegisteredClientId() : registeredClient.getClientId();
        String clientName = resolveClientName(authorizationConsent.getRegisteredClientId(), publicClientId);
        String tenantId = resolveTenantId(registeredClient, authorizationConsent.getPrincipalName());
        auditService.record("OAUTH_CONSENT_REVOKED", authorizationConsent.getPrincipalName(), tenantId, Map.of(
                "registeredClientId", authorizationConsent.getRegisteredClientId(),
                "clientId", publicClientId,
                "clientName", clientName,
                "principalName", authorizationConsent.getPrincipalName(),
                "authorities", authorizationConsent.getAuthorities().stream().map(granted -> granted.getAuthority()).toList()
        ));
    }

    @Override
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        return delegate.findById(registeredClientId, principalName);
    }

    private String resolveClientName(String registeredClientId, String fallback) {
        SysOauthClientEntity entity = sysOauthClientMapper.selectById(registeredClientId);
        return entity == null || !StringUtils.hasText(entity.getClientName()) ? fallback : entity.getClientName();
    }

    private String resolveTenantId(RegisteredClient registeredClient, String principalName) {
        String tenantId = TenantContext.getTenantId();
        if (StringUtils.hasText(tenantId)) {
            return tenantId;
        }
        if (registeredClient != null && StringUtils.hasText(registeredClient.getClientId())) {
            SysOauthClientEntity client = sysOauthClientMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysOauthClientEntity>()
                            .eq(SysOauthClientEntity::getClientId, registeredClient.getClientId())
                            .eq(SysOauthClientEntity::getDeleted, 0)
                            .last("limit 1"));
            if (client != null && StringUtils.hasText(client.getTenantId())) {
                return client.getTenantId();
            }
        }
        return StringUtils.hasText(principalName) ? "platform" : "platform";
    }
}
