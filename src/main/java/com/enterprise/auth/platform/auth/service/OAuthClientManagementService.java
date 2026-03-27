package com.enterprise.auth.platform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.auth.dto.CreateOauthClientRequest;
import com.enterprise.auth.platform.auth.dto.RotateOauthClientSecretRequest;
import com.enterprise.auth.platform.auth.dto.UpdateOauthClientRequest;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.time.TimeSupport;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientHistoryEntity;
import com.enterprise.auth.platform.persistence.entity.SysOauthScopeEntity;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientHistoryMapper;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import com.enterprise.auth.platform.persistence.mapper.SysOauthScopeMapper;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OAuthClientManagementService {

    public static final String CACHE_NAME = "oauth:clients";

    private final PersistenceProperties persistenceProperties;
    private final SysOauthClientMapper sysOauthClientMapper;
    private final SysOauthClientHistoryMapper sysOauthClientHistoryMapper;
    private final SysOauthScopeMapper sysOauthScopeMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public OAuthClientManagementService(
            PersistenceProperties persistenceProperties,
            @Nullable SysOauthClientMapper sysOauthClientMapper,
            @Nullable SysOauthClientHistoryMapper sysOauthClientHistoryMapper,
            @Nullable SysOauthScopeMapper sysOauthScopeMapper,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysOauthClientMapper = sysOauthClientMapper;
        this.sysOauthClientHistoryMapper = sysOauthClientHistoryMapper;
        this.sysOauthScopeMapper = sysOauthScopeMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = CACHE_NAME, key = "#root.target.clientListCacheKey()")
    public List<OAuthClientView> clients() {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        return sysOauthClientMapper.selectList(new LambdaQueryWrapper<SysOauthClientEntity>()
                        .eq(SysOauthClientEntity::getTenantId, tenantId)
                        .eq(SysOauthClientEntity::getDeleted, 0)
                        .orderByAsc(SysOauthClientEntity::getId))
                .stream()
                .map(entity -> toView(entity, null, false))
                .toList();
    }

    public OAuthClientView clientDetail(Long id) {
        requireDatabaseMode();
        return toView(getClient(id, currentTenantId()), null, true);
    }

    public OAuthClientScopeLinkageView scopeLinkage(Long id) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysOauthClientEntity entity = getClient(id, tenantId);
        List<String> clientScopes = split(entity.getScopes());
        List<OAuthClientScopeView> scopeDetails = resolveScopeDetails(tenantId, clientScopes);
        Map<String, SysOauthScopeEntity> configuredScopes = loadConfiguredScopes(tenantId);

        List<String> missingScopeCodes = clientScopes.stream()
                .filter(code -> !configuredScopes.containsKey(code))
                .distinct()
                .toList();

        List<String> recommendedScopeCodes = configuredScopes.values().stream()
                .filter(item -> item.getDefaultSelected() != null && item.getDefaultSelected() == 1)
                .map(SysOauthScopeEntity::getScopeCode)
                .filter(code -> !clientScopes.contains(code))
                .limit(8)
                .toList();

        List<String> actions = new ArrayList<>();
        if (!missingScopeCodes.isEmpty()) {
            actions.add("补齐缺失作用域定义，确保 consent 页和审计解释一致。");
        }
        boolean consentVisible = scopeDetails.stream().anyMatch(OAuthClientScopeView::visibleInConsent);
        if ((entity.getRequireConsent() != null && entity.getRequireConsent() == 1) && !consentVisible) {
            actions.add("当前客户端要求授权确认，但未配置可展示作用域，建议至少开启一个 visibleInConsent 作用域。");
        }
        if ((entity.getRequirePkce() == null || entity.getRequirePkce() == 0) && !StringUtils.hasText(entity.getClientSecret())) {
            actions.add("公共客户端建议启用 PKCE，降低授权码劫持风险。");
        }
        if (actions.isEmpty()) {
            actions.add("当前客户端与作用域定义联动正常。");
        }

        return new OAuthClientScopeLinkageView(
                entity.getId(),
                entity.getClientId(),
                entity.getClientName(),
                clientScopes,
                scopeDetails,
                missingScopeCodes,
                recommendedScopeCodes,
                actions
        );
    }

    @Transactional
    @CacheEvict(value = {
            CACHE_NAME,
            OAuthClientLookupCacheService.CACHE_NAME,
            OAuthScopeManagementService.CACHE_NAME
    }, allEntries = true)
    public OAuthClientView createClient(CreateOauthClientRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        ensureGlobalClientIdUnique(request.clientId(), null);
        SysOauthClientEntity existing = findClientByClientId(tenantId, request.clientId());
        if (existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0)) {
            throw new BusinessException("客户端编号已存在");
        }

        SysOauthClientEntity entity = existing == null ? new SysOauthClientEntity() : existing;
        entity.setTenantId(tenantId);
        entity.setClientId(request.clientId());
        entity.setClientSecret(resolveClientSecret(request.publicClient(), request.clientSecret(), null));
        entity.setClientName(request.clientName());
        entity.setRedirectUris(join(request.redirectUris()));
        entity.setScopes(join(request.scopes()));
        entity.setGrantTypes(join(request.grantTypes()));
        entity.setRequirePkce(Boolean.TRUE.equals(request.publicClient()) || Boolean.TRUE.equals(request.requirePkce()) ? 1 : 0);
        entity.setRequireConsent(Boolean.TRUE.equals(request.requireConsent()) ? 1 : 0);
        entity.setClientStatus(request.clientStatus() != null ? request.clientStatus() : 1);
        entity.setDeleted(0);

        if (existing == null) {
            sysOauthClientMapper.insert(entity);
        } else {
            sysOauthClientMapper.updateById(entity);
        }

        auditService.record("OAUTH_CLIENT_CREATED", SecuritySupport.currentOperator(), tenantId, Map.of(
                "clientId", request.clientId()
        ));
        recordClientHistory(tenantId, request.clientId(), "OAUTH_CLIENT_CREATED", "创建客户端", Map.of(
                "clientId", request.clientId()
        ));
        return toView(entity, Boolean.TRUE.equals(request.publicClient()) ? null : request.clientSecret(), true);
    }

    @Transactional
    @CacheEvict(value = {
            CACHE_NAME,
            OAuthClientLookupCacheService.CACHE_NAME,
            OAuthScopeManagementService.CACHE_NAME
    }, allEntries = true)
    public OAuthClientView updateClient(Long id, UpdateOauthClientRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysOauthClientEntity entity = getClient(id, tenantId);
        entity.setClientName(request.clientName());
        entity.setClientSecret(resolveClientSecret(request.publicClient(), request.clientSecret(), entity.getClientSecret()));
        entity.setRedirectUris(join(request.redirectUris()));
        entity.setScopes(join(request.scopes()));
        entity.setGrantTypes(join(request.grantTypes()));
        entity.setRequirePkce(Boolean.TRUE.equals(request.publicClient()) || Boolean.TRUE.equals(request.requirePkce()) ? 1 : 0);
        entity.setRequireConsent(Boolean.TRUE.equals(request.requireConsent()) ? 1 : 0);
        if (request.clientStatus() != null) {
            entity.setClientStatus(request.clientStatus());
        }
        sysOauthClientMapper.updateById(entity);
        auditService.record("OAUTH_CLIENT_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of(
                "clientId", entity.getClientId()
        ));
        recordClientHistory(tenantId, entity.getClientId(), "OAUTH_CLIENT_UPDATED", "更新客户端配置", Map.of(
                "clientId", entity.getClientId()
        ));
        return toView(
                entity,
                Boolean.TRUE.equals(request.publicClient())
                        ? null
                        : (StringUtils.hasText(request.clientSecret()) ? request.clientSecret() : null),
                true
        );
    }

    @Transactional
    @CacheEvict(value = {
            CACHE_NAME,
            OAuthClientLookupCacheService.CACHE_NAME,
            OAuthScopeManagementService.CACHE_NAME
    }, allEntries = true)
    public OAuthClientView updateClientStatus(Long id, boolean enabled) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysOauthClientEntity entity = getClient(id, tenantId);
        entity.setClientStatus(enabled ? 1 : 0);
        sysOauthClientMapper.updateById(entity);
        auditService.record("OAUTH_CLIENT_STATUS_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of(
                "clientId", entity.getClientId(),
                "enabled", enabled
        ));
        recordClientHistory(tenantId, entity.getClientId(), "OAUTH_CLIENT_STATUS_UPDATED",
                enabled ? "启用客户端" : "禁用客户端",
                Map.of("clientId", entity.getClientId(), "enabled", enabled));
        return toView(entity, null, true);
    }

    @Transactional
    @CacheEvict(value = {
            CACHE_NAME,
            OAuthClientLookupCacheService.CACHE_NAME,
            OAuthScopeManagementService.CACHE_NAME
    }, allEntries = true)
    public OAuthClientView rotateClientSecret(Long id, RotateOauthClientSecretRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysOauthClientEntity entity = getClient(id, tenantId);
        if (!StringUtils.hasText(entity.getClientSecret())) {
            throw new BusinessException("公共客户端不支持轮换密钥");
        }
        entity.setClientSecret(passwordEncoder.encode(request.clientSecret()));
        sysOauthClientMapper.updateById(entity);
        auditService.record("OAUTH_CLIENT_SECRET_ROTATED", SecuritySupport.currentOperator(), tenantId, Map.of(
                "clientId", entity.getClientId()
        ));
        recordClientHistory(tenantId, entity.getClientId(), "OAUTH_CLIENT_SECRET_ROTATED", "轮换客户端密钥", Map.of(
                "clientId", entity.getClientId()
        ));
        return toView(entity, request.clientSecret(), true);
    }

    @Transactional
    @CacheEvict(value = {
            CACHE_NAME,
            OAuthClientLookupCacheService.CACHE_NAME,
            OAuthScopeManagementService.CACHE_NAME
    }, allEntries = true)
    public void deleteClient(Long id) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysOauthClientEntity entity = getClient(id, tenantId);
        sysOauthClientMapper.deleteById(entity.getId());
        auditService.record("OAUTH_CLIENT_DELETED", SecuritySupport.currentOperator(), tenantId, Map.of(
                "clientId", entity.getClientId()
        ));
        recordClientHistory(tenantId, entity.getClientId(), "OAUTH_CLIENT_DELETED", "删除客户端", Map.of(
                "clientId", entity.getClientId()
        ));
    }

    private SysOauthClientEntity findClientByClientId(String tenantId, String clientId) {
        return sysOauthClientMapper.selectIncludingDeleted(tenantId, clientId);
    }

    private void ensureGlobalClientIdUnique(String clientId, Long currentId) {
        SysOauthClientEntity duplicated = sysOauthClientMapper.selectOne(new LambdaQueryWrapper<SysOauthClientEntity>()
                .eq(SysOauthClientEntity::getClientId, clientId)
                .eq(SysOauthClientEntity::getDeleted, 0)
                .ne(currentId != null, SysOauthClientEntity::getId, currentId)
                .last("limit 1"));
        if (duplicated != null) {
            throw new BusinessException("clientId 已被其他租户或客户端占用，请保持全局唯一");
        }
    }

    private SysOauthClientEntity getClient(Long id, String tenantId) {
        SysOauthClientEntity entity = sysOauthClientMapper.selectOne(new LambdaQueryWrapper<SysOauthClientEntity>()
                .eq(SysOauthClientEntity::getId, id)
                .eq(SysOauthClientEntity::getTenantId, tenantId)
                .eq(SysOauthClientEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("OAuth2 客户端不存在");
        }
        return entity;
    }

    private OAuthClientView toView(SysOauthClientEntity entity, String issuedClientSecret, boolean detailMode) {
        List<String> scopes = split(entity.getScopes());
        List<OAuthClientScopeView> scopeDetails = detailMode ? resolveScopeDetails(entity.getTenantId(), scopes) : List.of();
        return new OAuthClientView(
                entity.getId(),
                entity.getTenantId(),
                entity.getClientId(),
                entity.getClientName(),
                split(entity.getRedirectUris()),
                scopes,
                detailMode ? resolveScopeDescriptions(entity.getTenantId(), scopes) : Map.of(),
                scopeDetails,
                detailMode ? summarizeScopeTypes(scopeDetails) : Map.of(),
                split(entity.getGrantTypes()),
                !StringUtils.hasText(entity.getClientSecret()),
                entity.getRequirePkce() != null && entity.getRequirePkce() == 1,
                entity.getRequireConsent() != null && entity.getRequireConsent() == 1,
                entity.getClientStatus() == null || entity.getClientStatus() == 1,
                detailMode ? buildIntegrationGuidance(entity, scopeDetails) : null,
                issuedClientSecret,
                detailMode ? buildStatusHistory(entity) : List.of(),
                TimeSupport.toEpochMilli(entity.getCreatedAt()),
                TimeSupport.toEpochMilli(entity.getUpdatedAt())
        );
    }

    private Map<String, String> resolveScopeDescriptions(String tenantId, List<String> scopes) {
        if (sysOauthScopeMapper == null || scopes.isEmpty()) {
            return Map.of();
        }
        Set<String> normalizedScopes = scopes.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, String> descriptions = new LinkedHashMap<>();
        sysOauthScopeMapper.selectList(new LambdaQueryWrapper<SysOauthScopeEntity>()
                        .in(SysOauthScopeEntity::getTenantId, List.of("platform", tenantId))
                        .eq(SysOauthScopeEntity::getDeleted, 0)
                        .eq(SysOauthScopeEntity::getEnabled, 1)
                        .orderByAsc(SysOauthScopeEntity::getSortOrder)
                        .orderByAsc(SysOauthScopeEntity::getId))
                .forEach(item -> {
                    if (normalizedScopes.contains(item.getScopeCode())) {
                        descriptions.put(item.getScopeCode(), item.getScopeDesc());
                    }
                });
        normalizedScopes.forEach(scope -> descriptions.putIfAbsent(scope, "该作用域由客户端自定义声明，请按业务需要确认。"));
        return descriptions;
    }

    private List<OAuthClientScopeView> resolveScopeDetails(String tenantId, List<String> scopes) {
        if (scopes.isEmpty()) {
            return List.of();
        }
        Map<String, SysOauthScopeEntity> configured = loadConfiguredScopes(tenantId);
        Map<String, String> descriptionMap = resolveScopeDescriptions(tenantId, scopes);
        return new LinkedHashSet<>(scopes).stream()
                .map(scopeCode -> {
                    SysOauthScopeEntity configuredScope = configured.get(scopeCode);
                    return new OAuthClientScopeView(
                            scopeCode,
                            configuredScope == null ? scopeCode : configuredScope.getScopeName(),
                            configuredScope == null ? descriptionMap.get(scopeCode) : configuredScope.getScopeDesc(),
                            configuredScope == null ? "custom" : configuredScope.getScopeType(),
                            configuredScope == null || configuredScope.getVisibleInConsent() == null || configuredScope.getVisibleInConsent() == 1,
                            configuredScope != null && configuredScope.getDefaultSelected() != null && configuredScope.getDefaultSelected() == 1
                    );
                })
                .toList();
    }

    private Map<String, SysOauthScopeEntity> loadConfiguredScopes(String tenantId) {
        Map<String, SysOauthScopeEntity> configured = new LinkedHashMap<>();
        if (sysOauthScopeMapper == null) {
            return configured;
        }
        sysOauthScopeMapper.selectList(new LambdaQueryWrapper<SysOauthScopeEntity>()
                        .in(SysOauthScopeEntity::getTenantId, List.of("platform", tenantId))
                        .eq(SysOauthScopeEntity::getDeleted, 0)
                        .eq(SysOauthScopeEntity::getEnabled, 1)
                        .orderByAsc(SysOauthScopeEntity::getSortOrder)
                        .orderByAsc(SysOauthScopeEntity::getId))
                .forEach(item -> configured.putIfAbsent(item.getScopeCode(), item));
        return configured;
    }

    private Map<String, Long> summarizeScopeTypes(List<OAuthClientScopeView> scopeDetails) {
        return scopeDetails.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        item -> StringUtils.hasText(item.scopeType()) ? item.scopeType() : "default",
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));
    }

    private OAuthClientIntegrationGuidanceView buildIntegrationGuidance(
            SysOauthClientEntity entity,
            List<OAuthClientScopeView> scopeDetails
    ) {
        boolean publicClient = !StringUtils.hasText(entity.getClientSecret());
        String recommendedGrantType = publicClient ? "authorization_code" : "client_credentials";
        String summary = publicClient
                ? "推荐使用授权码模式并启用 PKCE，由前端发起授权跳转，认证中心完成登录和授权。"
                : "推荐在受控后端环境使用机密客户端模式，仅在服务端保存客户端密钥并换取令牌。";
        List<String> scopeTips = scopeDetails.stream()
                .map(item -> item.scopeName() + "：" + (StringUtils.hasText(item.scopeDesc()) ? item.scopeDesc() : "请补充作用域说明"))
                .limit(4)
                .toList();
        return new OAuthClientIntegrationGuidanceView(
                recommendedGrantType,
                entity.getRequirePkce() != null && entity.getRequirePkce() == 1,
                entity.getRequireConsent() != null && entity.getRequireConsent() == 1,
                summary,
                scopeTips
        );
    }

    private List<OAuthClientStatusHistoryView> buildStatusHistory(SysOauthClientEntity entity) {
        if (sysOauthClientHistoryMapper == null) {
            return List.of();
        }
        return sysOauthClientHistoryMapper.selectList(new LambdaQueryWrapper<SysOauthClientHistoryEntity>()
                        .eq(SysOauthClientHistoryEntity::getTenantId, entity.getTenantId())
                        .eq(SysOauthClientHistoryEntity::getClientId, entity.getClientId())
                        .orderByDesc(SysOauthClientHistoryEntity::getOccurredAt)
                        .orderByDesc(SysOauthClientHistoryEntity::getId))
                .stream()
                .limit(20)
                .map(this::toHistoryView)
                .toList();
    }

    private OAuthClientStatusHistoryView toHistoryView(SysOauthClientHistoryEntity entity) {
        Map<String, Object> payload = parsePayload(entity.getPayloadJson());
        return new OAuthClientStatusHistoryView(
                entity.getEventType(),
                entity.getSummary(),
                entity.getOperator(),
                TimeSupport.toEpochMilli(entity.getOccurredAt()),
                payload
        );
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private void recordClientHistory(String tenantId, String clientId, String eventType, String summary, Map<String, Object> payload) {
        if (sysOauthClientHistoryMapper == null) {
            return;
        }
        SysOauthClientHistoryEntity entity = new SysOauthClientHistoryEntity();
        entity.setTenantId(tenantId);
        entity.setClientId(clientId);
        entity.setEventType(eventType);
        entity.setSummary(summary);
        entity.setPayloadJson(writePayload(payload));
        entity.setOperator(SecuritySupport.currentOperator());
        entity.setOccurredAt(TimeSupport.utcNowDateTime());
        sysOauthClientHistoryMapper.insert(entity);
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private List<String> split(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String join(List<String> values) {
        return values.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String resolveClientSecret(Boolean publicClient, String rawSecret, String existingSecret) {
        if (Boolean.TRUE.equals(publicClient)) {
            return "";
        }
        if (StringUtils.hasText(rawSecret)) {
            return passwordEncoder.encode(rawSecret);
        }
        if (StringUtils.hasText(existingSecret)) {
            return existingSecret;
        }
        throw new BusinessException("机密客户端必须提供客户端密钥");
    }

    private void requireDatabaseMode() {
        if (!persistenceProperties.databaseEnabled() || sysOauthClientMapper == null) {
            throw new BusinessException("当前未启用数据库 OAuth2 客户端管理能力");
        }
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }

    public String clientListCacheKey() {
        return "list:" + currentTenantId();
    }

    @Schema(description = "OAuth2 客户端视图")
    public record OAuthClientView(
            @Schema(description = "主键 ID") Long id,
            @Schema(description = "所属租户") String tenantId,
            @Schema(description = "客户端编号") String clientId,
            @Schema(description = "客户端名称") String clientName,
            @Schema(description = "重定向地址列表") List<String> redirectUris,
            @Schema(description = "作用域列表") List<String> scopes,
            @Schema(description = "作用域说明，键为 scope 编码") Map<String, String> scopeDescriptions,
            @Schema(description = "作用域详情") List<OAuthClientScopeView> scopeDetails,
            @Schema(description = "作用域类型统计") Map<String, Long> scopeTypeSummary,
            @Schema(description = "授权类型列表") List<String> grantTypes,
            @Schema(description = "是否公共客户端") boolean publicClient,
            @Schema(description = "是否要求 PKCE") boolean requirePkce,
            @Schema(description = "是否要求授权确认") boolean requireConsent,
            @Schema(description = "是否启用") boolean enabled,
            @Schema(description = "接入建议") OAuthClientIntegrationGuidanceView integrationGuidance,
            @Schema(description = "本次返回的原始密钥，仅在创建或轮换时返回") String issuedClientSecret,
            @Schema(description = "最近客户端状态历史") List<OAuthClientStatusHistoryView> statusHistory,
            @Schema(description = "创建时间") Long createdAt,
            @Schema(description = "更新时间") Long updatedAt
    ) {
    }

    @Schema(description = "OAuth2 客户端作用域详情")
    public record OAuthClientScopeView(
            @Schema(description = "作用域编码") String scopeCode,
            @Schema(description = "作用域名称") String scopeName,
            @Schema(description = "作用域说明") String scopeDesc,
            @Schema(description = "作用域类型") String scopeType,
            @Schema(description = "是否在同意页展示") boolean visibleInConsent,
            @Schema(description = "是否默认选中") boolean defaultSelected
    ) {
    }

    @Schema(description = "OAuth2 客户端接入建议")
    public record OAuthClientIntegrationGuidanceView(
            @Schema(description = "推荐授权模式") String recommendedGrantType,
            @Schema(description = "是否建议启用 PKCE") boolean requirePkce,
            @Schema(description = "是否建议展示同意页") boolean requireConsent,
            @Schema(description = "接入摘要") String summary,
            @Schema(description = "作用域提示") List<String> scopeTips
    ) {
    }

    @Schema(description = "OAuth2 客户端状态历史视图")
    public record OAuthClientStatusHistoryView(
            @Schema(description = "事件类型") String eventType,
            @Schema(description = "事件摘要") String summary,
            @Schema(description = "操作人") String operator,
            @Schema(description = "发生时间") Long occurredAt,
            @Schema(description = "事件负载") Map<String, Object> payload
    ) {
    }
    @Schema(description = "OAuth2 客户端作用域联动引导")
    public record OAuthClientScopeLinkageView(
            @Schema(description = "主键 ID") Long id,
            @Schema(description = "客户端编码") String clientId,
            @Schema(description = "客户端名称") String clientName,
            @Schema(description = "客户端声明作用域") List<String> clientScopes,
            @Schema(description = "作用域详情") List<OAuthClientScopeView> scopeDetails,
            @Schema(description = "缺失定义的作用域编码") List<String> missingScopeCodes,
            @Schema(description = "建议补充到客户端的默认作用域") List<String> recommendedScopeCodes,
            @Schema(description = "联动建议") List<String> actions
    ) {
    }
}
