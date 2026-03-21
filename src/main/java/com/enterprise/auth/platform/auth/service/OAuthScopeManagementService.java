package com.enterprise.auth.platform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.auth.dto.OauthScopeCrudRequest;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.entity.SysOauthScopeEntity;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import com.enterprise.auth.platform.persistence.mapper.SysOauthScopeMapper;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OAuthScopeManagementService {

    private final PersistenceProperties persistenceProperties;
    private final SysOauthScopeMapper sysOauthScopeMapper;
    private final SysOauthClientMapper sysOauthClientMapper;
    private final AuditService auditService;

    public OAuthScopeManagementService(
            PersistenceProperties persistenceProperties,
            @Nullable SysOauthScopeMapper sysOauthScopeMapper,
            @Nullable SysOauthClientMapper sysOauthClientMapper,
            AuditService auditService
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysOauthScopeMapper = sysOauthScopeMapper;
        this.sysOauthClientMapper = sysOauthClientMapper;
        this.auditService = auditService;
    }

    public List<OAuthScopeView> scopes() {
        requirePlatformDatabaseMode();
        Map<String, ScopeReferenceHint> referenceHints = resolveScopeReferences();
        return sysOauthScopeMapper.selectList(new LambdaQueryWrapper<SysOauthScopeEntity>()
                        .eq(SysOauthScopeEntity::getTenantId, "platform")
                        .eq(SysOauthScopeEntity::getDeleted, 0)
                        .orderByAsc(SysOauthScopeEntity::getSortOrder)
                        .orderByAsc(SysOauthScopeEntity::getId))
                .stream()
                .map(entity -> toView(entity, referenceHints.get(entity.getScopeCode())))
                .toList();
    }

    @Transactional
    public OAuthScopeView createScope(OauthScopeCrudRequest request) {
        requirePlatformDatabaseMode();
        if (exists(request.scopeCode(), null)) {
            throw new BusinessException("作用域编码已存在");
        }
        SysOauthScopeEntity entity = new SysOauthScopeEntity();
        apply(entity, request);
        entity.setTenantId("platform");
        sysOauthScopeMapper.insert(entity);
        auditService.record("OAUTH_SCOPE_CREATED", SecuritySupport.currentOperator(), "platform",
                java.util.Map.of("scopeCode", request.scopeCode()));
        return toView(entity, resolveScopeReferences().get(entity.getScopeCode()));
    }

    @Transactional
    public OAuthScopeView updateScope(Long id, OauthScopeCrudRequest request) {
        requirePlatformDatabaseMode();
        SysOauthScopeEntity entity = getScope(id);
        if (exists(request.scopeCode(), id)) {
            throw new BusinessException("作用域编码已存在");
        }
        apply(entity, request);
        sysOauthScopeMapper.updateById(entity);
        auditService.record("OAUTH_SCOPE_UPDATED", SecuritySupport.currentOperator(), "platform",
                java.util.Map.of("scopeId", id, "scopeCode", request.scopeCode()));
        return toView(entity, resolveScopeReferences().get(entity.getScopeCode()));
    }

    @Transactional
    public void deleteScope(Long id) {
        requirePlatformDatabaseMode();
        SysOauthScopeEntity entity = getScope(id);
        ScopeReferenceHint referenceHint = resolveScopeReferences().get(entity.getScopeCode());
        if (referenceHint != null && referenceHint.referencedClientCount() > 0) {
            throw new BusinessException("当前作用域仍被客户端引用，无法删除");
        }
        sysOauthScopeMapper.deleteById(id);
        auditService.record("OAUTH_SCOPE_DELETED", SecuritySupport.currentOperator(), "platform",
                java.util.Map.of("scopeId", id, "scopeCode", entity.getScopeCode()));
    }

    private void apply(SysOauthScopeEntity entity, OauthScopeCrudRequest request) {
        entity.setScopeCode(request.scopeCode().trim());
        entity.setScopeName(request.scopeName().trim());
        entity.setScopeDesc(StringUtils.hasText(request.scopeDesc()) ? request.scopeDesc().trim() : null);
        entity.setScopeType(StringUtils.hasText(request.scopeType()) ? request.scopeType().trim() : null);
        entity.setDefaultSelected(Boolean.TRUE.equals(request.defaultSelected()) ? 1 : 0);
        entity.setVisibleInConsent(request.visibleInConsent() == null || Boolean.TRUE.equals(request.visibleInConsent()) ? 1 : 0);
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setEnabled(request.enabled() == null || Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
    }

    private boolean exists(String scopeCode, Long excludeId) {
        return sysOauthScopeMapper.selectCount(new LambdaQueryWrapper<SysOauthScopeEntity>()
                .eq(SysOauthScopeEntity::getTenantId, "platform")
                .eq(SysOauthScopeEntity::getScopeCode, scopeCode)
                .eq(SysOauthScopeEntity::getDeleted, 0)
                .ne(excludeId != null, SysOauthScopeEntity::getId, excludeId)) > 0;
    }

    private SysOauthScopeEntity getScope(Long id) {
        SysOauthScopeEntity entity = sysOauthScopeMapper.selectOne(new LambdaQueryWrapper<SysOauthScopeEntity>()
                .eq(SysOauthScopeEntity::getId, id)
                .eq(SysOauthScopeEntity::getTenantId, "platform")
                .eq(SysOauthScopeEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("OAuth2 作用域不存在");
        }
        return entity;
    }

    private OAuthScopeView toView(SysOauthScopeEntity entity, @Nullable ScopeReferenceHint referenceHint) {
        int referencedClientCount = referenceHint == null ? 0 : referenceHint.referencedClientCount();
        List<String> referencedClientIds = referenceHint == null ? List.of() : referenceHint.sampleClientIds();
        return new OAuthScopeView(
                entity.getId(),
                entity.getScopeCode(),
                entity.getScopeName(),
                entity.getScopeDesc(),
                entity.getScopeType(),
                entity.getDefaultSelected() != null && entity.getDefaultSelected() == 1,
                entity.getVisibleInConsent() == null || entity.getVisibleInConsent() == 1,
                entity.getSortOrder(),
                entity.getEnabled() == null || entity.getEnabled() == 1,
                entity.getUpdatedAt() == null ? entity.getCreatedAt() : entity.getUpdatedAt(),
                referencedClientCount,
                referencedClientIds
        );
    }

    private Map<String, ScopeReferenceHint> resolveScopeReferences() {
        if (sysOauthClientMapper == null) {
            return Map.of();
        }
        List<SysOauthClientEntity> clients = sysOauthClientMapper.selectList(new LambdaQueryWrapper<SysOauthClientEntity>()
                .eq(SysOauthClientEntity::getTenantId, "platform")
                .eq(SysOauthClientEntity::getDeleted, 0)
                .orderByAsc(SysOauthClientEntity::getId));
        if (clients.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> scopeClientIds = new LinkedHashMap<>();
        for (SysOauthClientEntity client : clients) {
            String clientId = StringUtils.hasText(client.getClientId()) ? client.getClientId().trim() : "";
            if (!StringUtils.hasText(clientId)) {
                continue;
            }
            for (String scopeCode : splitScopes(client.getScopes())) {
                scopeClientIds.computeIfAbsent(scopeCode, ignored -> new java.util.LinkedHashSet<>()).add(clientId);
            }
        }
        Map<String, ScopeReferenceHint> hints = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : scopeClientIds.entrySet()) {
            List<String> sampleClientIds = entry.getValue().stream().limit(5).toList();
            hints.put(entry.getKey(), new ScopeReferenceHint(entry.getValue().size(), sampleClientIds));
        }
        return hints;
    }

    private List<String> splitScopes(String scopes) {
        if (!StringUtils.hasText(scopes)) {
            return List.of();
        }
        return List.of(scopes.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void requirePlatformDatabaseMode() {
        if (!persistenceProperties.databaseEnabled() || sysOauthScopeMapper == null) {
            throw new BusinessException("当前未启用数据库 OAuth2 作用域管理能力");
        }
        String tenantId = TenantContext.getTenantId();
        if (StringUtils.hasText(tenantId) && !"platform".equals(tenantId)) {
            throw new BusinessException("仅平台租户允许维护作用域定义");
        }
    }

    @Schema(description = "OAuth2 作用域视图")
    public record OAuthScopeView(
            @Schema(description = "主键 ID") Long id,
            @Schema(description = "作用域编码") String scopeCode,
            @Schema(description = "作用域名称") String scopeName,
            @Schema(description = "作用域说明") String scopeDesc,
            @Schema(description = "作用域类型") String scopeType,
            @Schema(description = "是否默认选中") boolean defaultSelected,
            @Schema(description = "是否在同意页展示") boolean visibleInConsent,
            @Schema(description = "排序值") Integer sortOrder,
            @Schema(description = "是否启用") boolean enabled,
            @Schema(description = "更新时间") LocalDateTime updatedAt,
            @Schema(description = "引用该作用域的客户端数量") int referencedClientCount,
            @Schema(description = "引用该作用域的客户端示例（最多 5 条）") List<String> referencedClientIds
    ) {
    }

    private record ScopeReferenceHint(int referencedClientCount, List<String> sampleClientIds) {
    }
}
