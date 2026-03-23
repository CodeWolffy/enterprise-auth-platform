package com.enterprise.auth.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.config.FrontendProperties;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FrontendOauthClientInitializer {

    private final SysOauthClientMapper sysOauthClientMapper;
    private final FrontendProperties frontendProperties;
    private final PasswordEncoder passwordEncoder;

    public FrontendOauthClientInitializer(
            SysOauthClientMapper sysOauthClientMapper,
            FrontendProperties frontendProperties,
            PasswordEncoder passwordEncoder
    ) {
        this.sysOauthClientMapper = sysOauthClientMapper;
        this.frontendProperties = frontendProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void initializeFrontendClient() {
        if (!StringUtils.hasText(frontendProperties.publicClientId())) {
            return;
        }
        List<SysOauthClientEntity> duplicates = sysOauthClientMapper.selectList(new LambdaQueryWrapper<SysOauthClientEntity>()
                .eq(SysOauthClientEntity::getClientId, frontendProperties.publicClientId())
                .eq(SysOauthClientEntity::getDeleted, 0));
        if (duplicates.stream().map(SysOauthClientEntity::getTenantId).distinct().count() > 1) {
            throw new IllegalStateException("前端 OAuth2 client_id 必须全局唯一: " + frontendProperties.publicClientId());
        }
        SysOauthClientEntity existing = sysOauthClientMapper.selectOne(new LambdaQueryWrapper<SysOauthClientEntity>()
                .eq(SysOauthClientEntity::getTenantId, "platform")
                .eq(SysOauthClientEntity::getClientId, frontendProperties.publicClientId())
                .last("limit 1"));
        SysOauthClientEntity entity = existing == null ? new SysOauthClientEntity() : existing;
        entity.setTenantId("platform");
        entity.setClientId(frontendProperties.publicClientId());
        entity.setClientSecret(resolveClientSecret());
        entity.setClientName(StringUtils.hasText(frontendProperties.publicClientName()) ? frontendProperties.publicClientName() : "前端管理台");
        entity.setRedirectUris(frontendProperties.resolvedRedirectUris().stream().collect(Collectors.joining(",")));
        entity.setScopes(frontendProperties.resolvedScopes().stream().collect(Collectors.joining(",")));
        entity.setGrantTypes("authorization_code,refresh_token");
        entity.setRequirePkce(1);
        entity.setRequireConsent(1);
        entity.setDeleted(0);
        if (existing == null) {
            sysOauthClientMapper.insert(entity);
            return;
        }
        sysOauthClientMapper.updateById(entity);
    }

    private String resolveClientSecret() {
        if (!StringUtils.hasText(frontendProperties.publicClientSecret())) {
            return "";
        }
        return passwordEncoder.encode(frontendProperties.publicClientSecret());
    }
}
