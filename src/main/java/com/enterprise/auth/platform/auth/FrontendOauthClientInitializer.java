package com.enterprise.auth.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.config.FrontendProperties;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import jakarta.annotation.PostConstruct;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FrontendOauthClientInitializer {

    private final SysOauthClientMapper sysOauthClientMapper;
    private final FrontendProperties frontendProperties;

    public FrontendOauthClientInitializer(
            SysOauthClientMapper sysOauthClientMapper,
            FrontendProperties frontendProperties
    ) {
        this.sysOauthClientMapper = sysOauthClientMapper;
        this.frontendProperties = frontendProperties;
    }

    @PostConstruct
    public void initializeFrontendClient() {
        if (!StringUtils.hasText(frontendProperties.publicClientId())) {
            return;
        }
        SysOauthClientEntity existing = sysOauthClientMapper.selectOne(new LambdaQueryWrapper<SysOauthClientEntity>()
                .eq(SysOauthClientEntity::getTenantId, "platform")
                .eq(SysOauthClientEntity::getClientId, frontendProperties.publicClientId())
                .last("limit 1"));
        SysOauthClientEntity entity = existing == null ? new SysOauthClientEntity() : existing;
        entity.setTenantId("platform");
        entity.setClientId(frontendProperties.publicClientId());
        entity.setClientSecret("");
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
}
