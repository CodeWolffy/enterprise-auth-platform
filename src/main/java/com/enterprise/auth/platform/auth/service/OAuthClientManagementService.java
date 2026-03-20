package com.enterprise.auth.platform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.auth.dto.CreateOauthClientRequest;
import com.enterprise.auth.platform.auth.dto.RotateOauthClientSecretRequest;
import com.enterprise.auth.platform.auth.dto.UpdateOauthClientRequest;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OAuthClientManagementService {

    private final PersistenceProperties persistenceProperties;
    private final SysOauthClientMapper sysOauthClientMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public OAuthClientManagementService(
            PersistenceProperties persistenceProperties,
            @Nullable SysOauthClientMapper sysOauthClientMapper,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysOauthClientMapper = sysOauthClientMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public List<OAuthClientView> clients() {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        return sysOauthClientMapper.selectList(new LambdaQueryWrapper<SysOauthClientEntity>()
                        .eq(SysOauthClientEntity::getTenantId, tenantId)
                        .eq(SysOauthClientEntity::getDeleted, 0)
                        .orderByAsc(SysOauthClientEntity::getId))
                .stream()
                .map(entity -> toView(entity, null))
                .toList();
    }

    public OAuthClientView clientDetail(Long id) {
        requireDatabaseMode();
        return toView(getClient(id, currentTenantId()), null);
    }

    @Transactional
    public OAuthClientView createClient(CreateOauthClientRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
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

        auditService.record(
                "OAUTH_CLIENT_CREATED",
                SecuritySupport.currentOperator(),
                tenantId,
                Map.of("clientId", request.clientId())
        );
        return toView(entity, Boolean.TRUE.equals(request.publicClient()) ? null : request.clientSecret());
    }

    @Transactional
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
        auditService.record(
                "OAUTH_CLIENT_UPDATED",
                SecuritySupport.currentOperator(),
                tenantId,
                Map.of("clientId", entity.getClientId())
        );
        return toView(
                entity,
                Boolean.TRUE.equals(request.publicClient())
                        ? null
                        : (StringUtils.hasText(request.clientSecret()) ? request.clientSecret() : null)
        );
    }

    @Transactional
    public OAuthClientView updateClientStatus(Long id, boolean enabled) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysOauthClientEntity entity = getClient(id, tenantId);
        entity.setClientStatus(enabled ? 1 : 0);
        sysOauthClientMapper.updateById(entity);
        auditService.record(
                "OAUTH_CLIENT_STATUS_UPDATED",
                SecuritySupport.currentOperator(),
                tenantId,
                Map.of("clientId", entity.getClientId(), "enabled", enabled)
        );
        return toView(entity, null);
    }

    @Transactional
    public OAuthClientView rotateClientSecret(Long id, RotateOauthClientSecretRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysOauthClientEntity entity = getClient(id, tenantId);
        if (!StringUtils.hasText(entity.getClientSecret())) {
            throw new BusinessException("公共客户端不支持轮换密钥");
        }
        entity.setClientSecret(passwordEncoder.encode(request.clientSecret()));
        sysOauthClientMapper.updateById(entity);
        auditService.record(
                "OAUTH_CLIENT_SECRET_ROTATED",
                SecuritySupport.currentOperator(),
                tenantId,
                Map.of("clientId", entity.getClientId())
        );
        return toView(entity, request.clientSecret());
    }

    @Transactional
    public void deleteClient(Long id) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysOauthClientEntity entity = getClient(id, tenantId);
        sysOauthClientMapper.deleteById(entity.getId());
        auditService.record(
                "OAUTH_CLIENT_DELETED",
                SecuritySupport.currentOperator(),
                tenantId,
                Map.of("clientId", entity.getClientId())
        );
    }

    private SysOauthClientEntity findClientByClientId(String tenantId, String clientId) {
        return sysOauthClientMapper.selectIncludingDeleted(tenantId, clientId);
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

    private OAuthClientView toView(SysOauthClientEntity entity, String issuedClientSecret) {
        return new OAuthClientView(
                entity.getId(),
                entity.getTenantId(),
                entity.getClientId(),
                entity.getClientName(),
                split(entity.getRedirectUris()),
                split(entity.getScopes()),
                split(entity.getGrantTypes()),
                !StringUtils.hasText(entity.getClientSecret()),
                entity.getRequirePkce() != null && entity.getRequirePkce() == 1,
                entity.getRequireConsent() != null && entity.getRequireConsent() == 1,
                entity.getClientStatus() == null || entity.getClientStatus() == 1,
                issuedClientSecret,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
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

    @Schema(description = "OAuth2 客户端视图")
    public record OAuthClientView(
            @Schema(description = "主键 ID") Long id,
            @Schema(description = "所属租户") String tenantId,
            @Schema(description = "客户端编号") String clientId,
            @Schema(description = "客户端名称") String clientName,
            @Schema(description = "重定向地址列表") List<String> redirectUris,
            @Schema(description = "作用域列表") List<String> scopes,
            @Schema(description = "授权类型列表") List<String> grantTypes,
            @Schema(description = "是否公共客户端") boolean publicClient,
            @Schema(description = "是否要求 PKCE") boolean requirePkce,
            @Schema(description = "是否要求授权确认") boolean requireConsent,
            @Schema(description = "是否启用") boolean enabled,
            @Schema(description = "本次返回的原始密钥，仅在创建或轮换时返回") String issuedClientSecret,
            @Schema(description = "创建时间") LocalDateTime createdAt,
            @Schema(description = "更新时间") LocalDateTime updatedAt
    ) {
    }
}
