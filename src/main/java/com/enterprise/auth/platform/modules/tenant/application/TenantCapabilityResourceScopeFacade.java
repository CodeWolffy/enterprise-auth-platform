package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.menu.infrastructure.entity.SysMenuEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantCapabilityEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantCapabilityOverrideEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantCapabilityResourceScopeEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantPackageCapabilityEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantCapabilityMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantCapabilityOverrideMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantCapabilityResourceScopeMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantPackageCapabilityMapper;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TenantCapabilityResourceScopeFacade {

    private static final String PLATFORM_TENANT = "platform";
    private static final String SCOPE_VISIBLE = "VISIBLE";
    private static final String SCOPE_GRANT = "GRANT";

    private final SysTenantMapper sysTenantMapper;
    private final SysTenantCapabilityMapper sysTenantCapabilityMapper;
    private final SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper;
    private final SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper;
    private final SysTenantCapabilityResourceScopeMapper sysTenantCapabilityResourceScopeMapper;

    public TenantCapabilityResourceScopeFacade(
            SysTenantMapper sysTenantMapper,
            SysTenantCapabilityMapper sysTenantCapabilityMapper,
            SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper,
            SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper,
            SysTenantCapabilityResourceScopeMapper sysTenantCapabilityResourceScopeMapper
    ) {
        this.sysTenantMapper = sysTenantMapper;
        this.sysTenantCapabilityMapper = sysTenantCapabilityMapper;
        this.sysTenantPackageCapabilityMapper = sysTenantPackageCapabilityMapper;
        this.sysTenantCapabilityOverrideMapper = sysTenantCapabilityOverrideMapper;
        this.sysTenantCapabilityResourceScopeMapper = sysTenantCapabilityResourceScopeMapper;
    }

    public boolean shouldFilter(String activeTenantId) {
        String tenantId = normalizeTenantId(activeTenantId);
        return !PLATFORM_TENANT.equals(tenantId);
    }

    public Set<Long> visibleMenuIds(String activeTenantId, Collection<SysMenuEntity> menus) {
        String tenantId = normalizeTenantId(activeTenantId);
        if (!shouldFilter(tenantId) || menus == null || menus.isEmpty()) {
            return menuIds(menus);
        }
        AllowedResourceKeys allowedKeys = allowedResourceKeys(tenantId);
        if (allowedKeys.isEmpty()) {
            return Set.of();
        }
        Map<Long, SysMenuEntity> menuById = menuById(menus);
        Set<Long> allowedIds = new LinkedHashSet<>();
        for (SysMenuEntity menu : menus) {
            if (!isAllowedVisible(menu, allowedKeys)) {
                continue;
            }
            allowedIds.addAll(parseAncestors(menu.getAncestors(), menuById));
            if (menu.getId() != null) {
                allowedIds.add(menu.getId());
            }
        }
        return allowedIds;
    }

    public Set<Long> grantableMenuIds(String activeTenantId, Collection<SysMenuEntity> menus) {
        String tenantId = normalizeTenantId(activeTenantId);
        if (!shouldFilter(tenantId) || menus == null || menus.isEmpty()) {
            return menuIds(menus);
        }
        AllowedResourceKeys allowedKeys = allowedResourceKeys(tenantId);
        if (allowedKeys.isEmpty()) {
            return Set.of();
        }
        Map<Long, SysMenuEntity> menuById = menuById(menus);
        Set<Long> allowedIds = new LinkedHashSet<>();
        for (SysMenuEntity menu : menus) {
            if (!isAllowedGrant(menu, allowedKeys)) {
                continue;
            }
            allowedIds.addAll(parseAncestors(menu.getAncestors(), menuById));
            if (menu.getId() != null) {
                allowedIds.add(menu.getId());
            }
        }
        return allowedIds;
    }

    public Set<String> filterGrantKeys(String activeTenantId, Collection<SysMenuEntity> menus, Collection<String> grantKeys) {
        String tenantId = normalizeTenantId(activeTenantId);
        if (!shouldFilter(tenantId) || grantKeys == null || grantKeys.isEmpty()) {
            return normalizeTextSet(grantKeys);
        }
        Set<Long> grantableIds = grantableMenuIds(tenantId, menus);
        if (grantableIds.isEmpty()) {
            return Set.of();
        }
        Set<String> requested = normalizeTextSet(grantKeys);
        return menus.stream()
                .filter(menu -> menu.getId() != null && grantableIds.contains(menu.getId()))
                .map(SysMenuEntity::getGrantKey)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(requested::contains)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private AllowedResourceKeys allowedResourceKeys(String tenantId) {
        Set<String> capabilityCodes = effectiveCapabilityCodes(tenantId);
        if (capabilityCodes.isEmpty()) {
            return AllowedResourceKeys.empty();
        }
        List<SysTenantCapabilityResourceScopeEntity> scopes = withPlatformTenant(() -> sysTenantCapabilityResourceScopeMapper.selectList(
                new LambdaQueryWrapper<SysTenantCapabilityResourceScopeEntity>()
                        .eq(SysTenantCapabilityResourceScopeEntity::getTenantId, PLATFORM_TENANT)
                        .in(SysTenantCapabilityResourceScopeEntity::getCapabilityCode, capabilityCodes)
        ));
        Set<String> visible = new LinkedHashSet<>();
        Set<String> grant = new LinkedHashSet<>();
        for (SysTenantCapabilityResourceScopeEntity scope : scopes) {
            if (!StringUtils.hasText(scope.getResourceKey())) {
                continue;
            }
            String key = scope.getResourceKey().trim();
            String type = StringUtils.hasText(scope.getScopeType()) ? scope.getScopeType().trim().toUpperCase() : SCOPE_VISIBLE;
            if (SCOPE_GRANT.equals(type)) {
                grant.add(key);
                continue;
            }
            visible.add(key);
        }
        return new AllowedResourceKeys(visible, grant);
    }

    private Set<String> effectiveCapabilityCodes(String tenantId) {
        SysTenantEntity tenant = TenantContext.runWithTenant(tenantId, () -> sysTenantMapper.selectOne(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getTenantId, tenantId)
                .eq(SysTenantEntity::getDeleted, 0)
                .last("limit 1")));
        if (tenant == null) {
            return Set.of();
        }
        Set<String> enabledCapabilityCodes = withPlatformTenant(() -> sysTenantCapabilityMapper.selectList(
                        new LambdaQueryWrapper<SysTenantCapabilityEntity>()
                                .eq(SysTenantCapabilityEntity::getTenantId, PLATFORM_TENANT)
                                .eq(SysTenantCapabilityEntity::getDeleted, 0)
                                .eq(SysTenantCapabilityEntity::getEnabled, 1))
                .stream()
                .map(SysTenantCapabilityEntity::getCapabilityCode)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        if (enabledCapabilityCodes.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        if (StringUtils.hasText(tenant.getPackageCode())) {
            List<SysTenantPackageCapabilityEntity> packageCapabilities = withPlatformTenant(() -> sysTenantPackageCapabilityMapper.selectList(
                    new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                            .eq(SysTenantPackageCapabilityEntity::getTenantId, PLATFORM_TENANT)
                            .eq(SysTenantPackageCapabilityEntity::getPackageCode, tenant.getPackageCode())));
            for (SysTenantPackageCapabilityEntity relation : packageCapabilities) {
                addEnabledCapability(result, enabledCapabilityCodes, relation.getCapabilityCode());
            }
        }
        List<SysTenantCapabilityOverrideEntity> overrides = TenantContext.runWithTenant(tenantId, () -> sysTenantCapabilityOverrideMapper.selectList(
                new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                        .eq(SysTenantCapabilityOverrideEntity::getTenantId, tenantId)));
        for (SysTenantCapabilityOverrideEntity override : overrides) {
            String code = normalizeText(override.getCapabilityCode());
            if (!StringUtils.hasText(code) || !enabledCapabilityCodes.contains(code)) {
                continue;
            }
            if (override.getEnabled() != null && override.getEnabled() == 1) {
                result.add(code);
            } else {
                result.remove(code);
            }
        }
        return result;
    }

    private boolean isAllowedVisible(SysMenuEntity menu, AllowedResourceKeys allowedKeys) {
        if (allowedKeys.visible().contains(normalizeText(menu.getResourceKey()))) {
            return true;
        }
        String grantKey = normalizeText(menu.getGrantKey());
        return StringUtils.hasText(grantKey) && allowedKeys.grant().contains(grantKey);
    }

    private boolean isAllowedGrant(SysMenuEntity menu, AllowedResourceKeys allowedKeys) {
        if (allowedKeys.grant().contains(normalizeText(menu.getGrantKey()))) {
            return true;
        }
        return allowedKeys.visible().contains(normalizeText(menu.getResourceKey()));
    }

    private void addEnabledCapability(Set<String> result, Set<String> enabledCapabilityCodes, String code) {
        String normalized = normalizeText(code);
        if (StringUtils.hasText(normalized) && enabledCapabilityCodes.contains(normalized)) {
            result.add(normalized);
        }
    }

    private Map<Long, SysMenuEntity> menuById(Collection<SysMenuEntity> menus) {
        Map<Long, SysMenuEntity> result = new LinkedHashMap<>();
        for (SysMenuEntity menu : menus) {
            if (menu.getId() != null) {
                result.put(menu.getId(), menu);
            }
        }
        return result;
    }

    private Set<Long> menuIds(Collection<SysMenuEntity> menus) {
        if (menus == null || menus.isEmpty()) {
            return Set.of();
        }
        return menus.stream()
                .map(SysMenuEntity::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> parseAncestors(String ancestors, Map<Long, SysMenuEntity> menuById) {
        if (!StringUtils.hasText(ancestors)) {
            return Set.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String part : ancestors.split(",")) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            try {
                Long id = Long.parseLong(part.trim());
                if (menuById.containsKey(id)) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // ignore invalid ancestor marker
            }
        }
        return ids;
    }

    private Set<String> normalizeTextSet(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeTenantId(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId.trim() : PLATFORM_TENANT;
    }

    private <T> T withPlatformTenant(Supplier<T> supplier) {
        return TenantContext.runWithTenant(PLATFORM_TENANT, supplier);
    }

    private record AllowedResourceKeys(Set<String> visible, Set<String> grant) {
        static AllowedResourceKeys empty() {
            return new AllowedResourceKeys(Set.of(), Set.of());
        }

        boolean isEmpty() {
            return visible.isEmpty() && grant.isEmpty();
        }
    }
}