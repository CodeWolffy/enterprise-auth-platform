package com.enterprise.auth.platform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.auth.platform.auth.dto.ConsentView;
import com.enterprise.auth.platform.common.model.PageResult;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.entity.SysOauthConsentEntity;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import com.enterprise.auth.platform.persistence.mapper.SysOauthConsentMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OauthConsentService {

    private final SysOauthConsentMapper sysOauthConsentMapper;
    private final SysOauthClientMapper sysOauthClientMapper;
    @SuppressWarnings("unused")
    private final OAuth2AuthorizationConsentService authorizationConsentService;

    public OauthConsentService(
            SysOauthConsentMapper sysOauthConsentMapper,
            SysOauthClientMapper sysOauthClientMapper,
            OAuth2AuthorizationConsentService authorizationConsentService
    ) {
        this.sysOauthConsentMapper = sysOauthConsentMapper;
        this.sysOauthClientMapper = sysOauthClientMapper;
        this.authorizationConsentService = authorizationConsentService;
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
        sysOauthConsentMapper.delete(new LambdaQueryWrapper<SysOauthConsentEntity>()
                .eq(SysOauthConsentEntity::getRegisteredClientId, registeredClientId)
                .eq(SysOauthConsentEntity::getPrincipalName, principalName));
    }

    private ConsentView toView(SysOauthConsentEntity consent, SysOauthClientEntity client) {
        String publicClientId = client == null ? consent.getRegisteredClientId() : client.getClientId();
        String clientName = client == null ? "未知客户端" : client.getClientName();
        return new ConsentView(
                consent.getRegisteredClientId(),
                publicClientId,
                clientName,
                consent.getPrincipalName(),
                splitAuthorities(consent.getAuthorities())
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
}
