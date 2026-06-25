package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantCapabilityEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantCapabilityOverrideEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantCapabilityResourceScopeEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantPackageCapabilityEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantPackageEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantCapabilityMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantCapabilityOverrideMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantCapabilityResourceScopeMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantPackageCapabilityMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantPackageMapper;
import com.enterprise.auth.platform.common.authz.SecuritySupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.tenant.interfaces.TenantCapabilityCrudRequest;
import com.enterprise.auth.platform.modules.tenant.interfaces.TenantPackageCrudRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TenantCatalogManagementService {

    private final SysTenantPackageMapper sysTenantPackageMapper;
    private final SysTenantCapabilityMapper sysTenantCapabilityMapper;
    private final SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper;
    private final SysTenantMapper sysTenantMapper;
    private final SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper;
    private final SysTenantCapabilityResourceScopeMapper sysTenantCapabilityResourceScopeMapper;
    private final AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService;

    public TenantCatalogManagementService(
            SysTenantPackageMapper sysTenantPackageMapper,
            SysTenantCapabilityMapper sysTenantCapabilityMapper,
            SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper,
            SysTenantMapper sysTenantMapper,
            SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper,
            SysTenantCapabilityResourceScopeMapper sysTenantCapabilityResourceScopeMapper,
            AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService
    ) {
        this.sysTenantPackageMapper = sysTenantPackageMapper;
        this.sysTenantCapabilityMapper = sysTenantCapabilityMapper;
        this.sysTenantPackageCapabilityMapper = sysTenantPackageCapabilityMapper;
        this.sysTenantMapper = sysTenantMapper;
        this.sysTenantCapabilityOverrideMapper = sysTenantCapabilityOverrideMapper;
        this.sysTenantCapabilityResourceScopeMapper = sysTenantCapabilityResourceScopeMapper;
        this.permissionSnapshotInvalidationService = permissionSnapshotInvalidationService;
    }

    public List<TenantPackageView> packages() {
        requirePlatformTenant();
        Map<String, List<String>> packageCapabilities = loadPackageCapabilities();
        Map<String, PackageReferenceHint> packageReferences = loadPackageReferences();
        return sysTenantPackageMapper.selectList(new LambdaQueryWrapper<SysTenantPackageEntity>()
                        .eq(SysTenantPackageEntity::getTenantId, "platform")
                        .eq(SysTenantPackageEntity::getDeleted, 0)
                        .orderByAsc(SysTenantPackageEntity::getOrderNo)
                        .orderByAsc(SysTenantPackageEntity::getId))
                .stream()
                .map(item -> toPackageView(
                        item,
                        packageCapabilities.getOrDefault(item.getPackageCode(), List.of()),
                        packageReferences.get(item.getPackageCode())
                ))
                .toList();
    }

    public List<TenantCapabilityView> capabilities() {
        requirePlatformTenant();
        Map<String, CapabilityReferenceHint> capabilityReferences = loadCapabilityReferences();
        return sysTenantCapabilityMapper.selectList(new LambdaQueryWrapper<SysTenantCapabilityEntity>()
                        .eq(SysTenantCapabilityEntity::getTenantId, "platform")
                        .eq(SysTenantCapabilityEntity::getDeleted, 0)
                        .orderByAsc(SysTenantCapabilityEntity::getSortOrder)
                        .orderByAsc(SysTenantCapabilityEntity::getId))
                .stream()
                .map(item -> toCapabilityView(item, capabilityReferences.get(item.getCapabilityCode())))
                .toList();
    }

    public TenantPackageImpactView packageImpact(Long id) {
        requirePlatformTenant();
        SysTenantPackageEntity entity = getPackage(id);
        Map<String, List<String>> packageCapabilities = loadPackageCapabilities();
        Map<String, PackageReferenceHint> packageReferences = loadPackageReferences();
        List<String> capabilityCodes = packageCapabilities.getOrDefault(entity.getPackageCode(), List.of());
        PackageReferenceHint referenceHint = packageReferences.get(entity.getPackageCode());
        ResourceScopeSummary resourceScopeSummary = summarizeResourceScope(capabilityCodes);
        int referencedTenantCount = referenceHint == null ? 0 : referenceHint.referencedTenantCount();
        List<String> referencedTenantIds = referenceHint == null ? List.of() : referenceHint.sampleTenantIds();

        List<ImpactRuleView> rules = new ArrayList<>();
        rules.add(new ImpactRuleView(
                "PACKAGE_REFERENCED_TENANTS",
                "ERROR",
                referencedTenantCount > 0,
                "套餐被租户引用时不允许直接删除，需先迁移租户。",
                referencedTenantCount,
                true
        ));
        rules.add(new ImpactRuleView(
                "PACKAGE_EMPTY_CAPABILITIES",
                "WARN",
                capabilityCodes.isEmpty(),
                "套餐未配置任何能力，租户接入后可能无法使用业务模块。",
                capabilityCodes.size(),
                false
        ));
        rules.add(new ImpactRuleView(
                "PACKAGE_DISABLED_WHILE_REFERENCED",
                "WARN",
                (entity.getEnabled() != null && entity.getEnabled() == 0) && referencedTenantCount > 0,
                "已被引用的套餐处于停用状态，可能导致租户功能可用性下降。",
                referencedTenantCount,
                false
        ));

        List<String> actions = new ArrayList<>();
        if (referencedTenantCount > 0) {
            actions.add("先将引用租户迁移到新套餐，再执行删除或高风险变更。");
        }
        if (capabilityCodes.isEmpty()) {
            actions.add("至少补齐一项基础能力（如 auth、user、audit）后再下发到租户。");
        }
        if (actions.isEmpty()) {
            actions.add("当前未发现阻断项，可按变更流程执行。");
        }

        return new TenantPackageImpactView(
                entity.getId(),
                entity.getPackageCode(),
                entity.getPackageName(),
                entity.getEnabled() == null || entity.getEnabled() == 1,
                capabilityCodes,
                resourceScopeSummary.visibleResourceCount(),
                resourceScopeSummary.grantResourceCount(),
                resourceScopeSummary.sampleResourceKeys(),
                referencedTenantCount,
                referencedTenantIds,
                rules,
                actions
        );
    }

    public TenantCapabilityImpactView capabilityImpact(Long id) {
        requirePlatformTenant();
        SysTenantCapabilityEntity entity = getCapability(id);
        Map<String, CapabilityReferenceHint> capabilityReferences = loadCapabilityReferences();
        CapabilityReferenceHint hint = capabilityReferences.get(entity.getCapabilityCode());
        int referencedPackageCount = hint == null ? 0 : hint.referencedPackageCount();
        int referencedTenantCount = hint == null ? 0 : hint.referencedTenantCount();
        int overrideReferenceCount = hint == null ? 0 : hint.overrideReferenceCount();
        List<String> referencedPackageCodes = hint == null ? List.of() : hint.samplePackageCodes();
        List<String> referencedTenantIds = hint == null ? List.of() : hint.sampleTenantIds();

        List<ImpactRuleView> rules = new ArrayList<>();
        rules.add(new ImpactRuleView(
                "CAPABILITY_REFERENCED_PACKAGES",
                "ERROR",
                referencedPackageCount > 0,
                "能力被套餐引用时不允许直接删除，需先解除套餐绑定。",
                referencedPackageCount,
                true
        ));
        rules.add(new ImpactRuleView(
                "CAPABILITY_OVERRIDE_REFERENCES",
                "ERROR",
                overrideReferenceCount > 0,
                "能力存在租户覆盖记录时不允许直接删除，需先清理覆盖配置。",
                overrideReferenceCount,
                true
        ));
        rules.add(new ImpactRuleView(
                "CAPABILITY_DISABLED_WHILE_REFERENCED",
                "WARN",
                (entity.getEnabled() != null && entity.getEnabled() == 0) && (referencedPackageCount > 0 || overrideReferenceCount > 0),
                "能力已停用但仍有引用，可能导致租户侧功能不一致。",
                referencedTenantCount,
                false
        ));

        List<String> actions = new ArrayList<>();
        if (referencedPackageCount > 0) {
            actions.add("先在套餐中移除该能力，再执行删除。");
        }
        if (overrideReferenceCount > 0) {
            actions.add("清理租户能力覆盖记录后再执行删除。");
        }
        if (actions.isEmpty()) {
            actions.add("当前未发现阻断项，可按变更流程执行。");
        }

        return new TenantCapabilityImpactView(
                entity.getId(),
                entity.getCapabilityCode(),
                entity.getCapabilityName(),
                entity.getEnabled() == null || entity.getEnabled() == 1,
                referencedPackageCount,
                referencedPackageCodes,
                referencedTenantCount,
                referencedTenantIds,
                overrideReferenceCount,
                rules,
                actions
        );
    }

    @Transactional
    public TenantPackageView createPackage(TenantPackageCrudRequest request) {
        requirePlatformTenant();
        String packageCode = normalizeCode(request.packageCode());
        if (packageExists(packageCode, null)) {
            throw new BusinessException("套餐编码已存在");
        }
        ensureCapabilitiesExist(request.capabilityCodes());
        SysTenantPackageEntity entity = new SysTenantPackageEntity();
        apply(entity, request);
        entity.setTenantId("platform");
        sysTenantPackageMapper.insert(entity);
        replacePackageCapabilities(packageCode, request.capabilityCodes());
        evictPrincipalSnapshots();
        return toPackageView(entity, normalizedCodes(request.capabilityCodes()), loadPackageReferences().get(packageCode));
    }

    @Transactional
    public TenantPackageView updatePackage(Long id, TenantPackageCrudRequest request) {
        requirePlatformTenant();
        SysTenantPackageEntity entity = getPackage(id);
        String packageCode = normalizeCode(request.packageCode());
        if (packageExists(packageCode, id)) {
            throw new BusinessException("套餐编码已存在");
        }
        ensureCapabilitiesExist(request.capabilityCodes());
        String oldCode = entity.getPackageCode();
        apply(entity, request);
        sysTenantPackageMapper.updateById(entity);
        if (!oldCode.equals(packageCode)) {
            migratePackageReference(oldCode, packageCode);
        }
        replacePackageCapabilities(packageCode, request.capabilityCodes());
        evictPrincipalSnapshots();
        return toPackageView(entity, normalizedCodes(request.capabilityCodes()), loadPackageReferences().get(packageCode));
    }

    @Transactional
    public void deletePackage(Long id) {
        requirePlatformTenant();
        SysTenantPackageEntity entity = getPackage(id);
        PackageReferenceHint referenceHint = loadPackageReferences().get(entity.getPackageCode());
        if (referenceHint != null && referenceHint.referencedTenantCount() > 0) {
            throw new BusinessException("该套餐仍被租户使用，暂不允许删除");
        }
        sysTenantPackageMapper.deleteById(id);
        sysTenantPackageCapabilityMapper.delete(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                .eq(SysTenantPackageCapabilityEntity::getPackageCode, entity.getPackageCode()));
        evictPrincipalSnapshots();
    }

    @Transactional
    public TenantCapabilityView createCapability(TenantCapabilityCrudRequest request) {
        requirePlatformTenant();
        String capabilityCode = normalizeCode(request.capabilityCode());
        if (capabilityExists(capabilityCode, null)) {
            throw new BusinessException("能力编码已存在");
        }
        SysTenantCapabilityEntity entity = new SysTenantCapabilityEntity();
        apply(entity, request);
        entity.setTenantId("platform");
        sysTenantCapabilityMapper.insert(entity);
        evictPrincipalSnapshots();
        return toCapabilityView(entity, loadCapabilityReferences().get(capabilityCode));
    }

    @Transactional
    public TenantCapabilityView updateCapability(Long id, TenantCapabilityCrudRequest request) {
        requirePlatformTenant();
        SysTenantCapabilityEntity entity = getCapability(id);
        String capabilityCode = normalizeCode(request.capabilityCode());
        if (capabilityExists(capabilityCode, id)) {
            throw new BusinessException("能力编码已存在");
        }
        String oldCode = entity.getCapabilityCode();
        apply(entity, request);
        sysTenantCapabilityMapper.updateById(entity);
        if (!oldCode.equals(capabilityCode)) {
            migrateCapabilityReference(oldCode, capabilityCode);
        }
        evictPrincipalSnapshots();
        return toCapabilityView(entity, loadCapabilityReferences().get(capabilityCode));
    }

    @Transactional
    public void deleteCapability(Long id) {
        requirePlatformTenant();
        SysTenantCapabilityEntity entity = getCapability(id);
        long packageRefCount = sysTenantPackageCapabilityMapper.selectCount(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                .eq(SysTenantPackageCapabilityEntity::getCapabilityCode, entity.getCapabilityCode()));
        if (packageRefCount > 0) {
            throw new BusinessException("该能力仍被套餐引用，暂不允许删除");
        }
        long overrideRefCount = loadAllTenantCapabilityOverrides().stream()
                .filter(override -> entity.getCapabilityCode().equals(override.getCapabilityCode()))
                .count();
        if (overrideRefCount > 0) {
            throw new BusinessException("该能力存在租户覆盖记录，暂不允许删除");
        }
        sysTenantCapabilityMapper.deleteById(id);
        evictPrincipalSnapshots();
    }

    private void evictPrincipalSnapshots() {
        permissionSnapshotInvalidationService.invalidateAll();
    }

    private Map<String, List<String>> loadPackageCapabilities() {
        return sysTenantPackageCapabilityMapper.selectList(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                        .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform"))
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        SysTenantPackageCapabilityEntity::getPackageCode,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(SysTenantPackageCapabilityEntity::getCapabilityCode, java.util.stream.Collectors.toList())
                ));
    }

    private Map<String, PackageReferenceHint> loadPackageReferences() {
        Map<String, Set<String>> tenantIdsByPackageCode = new LinkedHashMap<>();
        for (SysTenantEntity tenant : sysTenantMapper.selectList(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0)
                .orderByAsc(SysTenantEntity::getId))) {
            if (!StringUtils.hasText(tenant.getPackageCode()) || !StringUtils.hasText(tenant.getTenantId())) {
                continue;
            }
            tenantIdsByPackageCode
                    .computeIfAbsent(tenant.getPackageCode().trim(), ignored -> new LinkedHashSet<>())
                    .add(tenant.getTenantId().trim());
        }
        Map<String, PackageReferenceHint> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : tenantIdsByPackageCode.entrySet()) {
            result.put(entry.getKey(), new PackageReferenceHint(entry.getValue().size(), entry.getValue().stream().limit(5).toList()));
        }
        return result;
    }

    private ResourceScopeSummary summarizeResourceScope(List<String> capabilityCodes) {
        List<String> normalizedCodes = normalizedCodes(capabilityCodes);
        if (normalizedCodes.isEmpty()) {
            return new ResourceScopeSummary(0, 0, List.of());
        }
        List<SysTenantCapabilityResourceScopeEntity> scopes = sysTenantCapabilityResourceScopeMapper.selectList(
                new LambdaQueryWrapper<SysTenantCapabilityResourceScopeEntity>()
                        .eq(SysTenantCapabilityResourceScopeEntity::getTenantId, "platform")
                        .in(SysTenantCapabilityResourceScopeEntity::getCapabilityCode, normalizedCodes)
        );
        Set<String> visibleKeys = new LinkedHashSet<>();
        Set<String> grantKeys = new LinkedHashSet<>();
        Set<String> samples = new LinkedHashSet<>();
        for (SysTenantCapabilityResourceScopeEntity scope : scopes) {
            if (!StringUtils.hasText(scope.getResourceKey())) {
                continue;
            }
            String resourceKey = scope.getResourceKey().trim();
            if ("GRANT".equalsIgnoreCase(scope.getScopeType())) {
                grantKeys.add(resourceKey);
            } else {
                visibleKeys.add(resourceKey);
            }
            if (samples.size() < 8) {
                samples.add(resourceKey);
            }
        }
        return new ResourceScopeSummary(visibleKeys.size(), grantKeys.size(), samples.stream().toList());
    }

    private Map<String, CapabilityReferenceHint> loadCapabilityReferences() {
        Map<String, Set<String>> packageCodesByCapability = new LinkedHashMap<>();
        for (SysTenantPackageCapabilityEntity relation : sysTenantPackageCapabilityMapper.selectList(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform"))) {
            if (!StringUtils.hasText(relation.getCapabilityCode()) || !StringUtils.hasText(relation.getPackageCode())) {
                continue;
            }
            packageCodesByCapability
                    .computeIfAbsent(relation.getCapabilityCode().trim(), ignored -> new LinkedHashSet<>())
                    .add(relation.getPackageCode().trim());
        }

        Map<String, Set<String>> tenantIdsByCapability = new LinkedHashMap<>();
        Map<String, Set<String>> capabilitiesByPackageCode = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : packageCodesByCapability.entrySet()) {
            for (String packageCode : entry.getValue()) {
                capabilitiesByPackageCode.computeIfAbsent(packageCode, ignored -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }
        for (SysTenantEntity tenant : sysTenantMapper.selectList(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0)
                .orderByAsc(SysTenantEntity::getId))) {
            if (!StringUtils.hasText(tenant.getTenantId()) || !StringUtils.hasText(tenant.getPackageCode())) {
                continue;
            }
            for (String capabilityCode : capabilitiesByPackageCode.getOrDefault(tenant.getPackageCode().trim(), Set.of())) {
                tenantIdsByCapability
                        .computeIfAbsent(capabilityCode, ignored -> new LinkedHashSet<>())
                        .add(tenant.getTenantId().trim());
            }
        }

        Map<String, Integer> overrideRefCounts = new LinkedHashMap<>();
        for (SysTenantCapabilityOverrideEntity override : loadAllTenantCapabilityOverrides()) {
            if (!StringUtils.hasText(override.getCapabilityCode())) {
                continue;
            }
            String code = override.getCapabilityCode().trim();
            overrideRefCounts.put(code, overrideRefCounts.getOrDefault(code, 0) + 1);
        }

        Set<String> allCodes = new LinkedHashSet<>();
        allCodes.addAll(packageCodesByCapability.keySet());
        allCodes.addAll(tenantIdsByCapability.keySet());
        allCodes.addAll(overrideRefCounts.keySet());

        Map<String, CapabilityReferenceHint> result = new LinkedHashMap<>();
        for (String code : allCodes) {
            Set<String> packageCodes = packageCodesByCapability.getOrDefault(code, Set.of());
            Set<String> tenantIds = tenantIdsByCapability.getOrDefault(code, Set.of());
            int overrideCount = overrideRefCounts.getOrDefault(code, 0);
            result.put(code, new CapabilityReferenceHint(
                    packageCodes.size(),
                    packageCodes.stream().limit(5).toList(),
                    tenantIds.size(),
                    tenantIds.stream().limit(5).toList(),
                    overrideCount
            ));
        }
        return result;
    }

    private void replacePackageCapabilities(String packageCode, List<String> capabilityCodes) {
        sysTenantPackageCapabilityMapper.delete(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                .eq(SysTenantPackageCapabilityEntity::getPackageCode, packageCode));
        for (String capabilityCode : normalizedCodes(capabilityCodes)) {
            SysTenantPackageCapabilityEntity relation = new SysTenantPackageCapabilityEntity();
            relation.setTenantId("platform");
            relation.setPackageCode(packageCode);
            relation.setCapabilityCode(capabilityCode);
            sysTenantPackageCapabilityMapper.insert(relation);
        }
    }

    private List<SysTenantCapabilityOverrideEntity> loadAllTenantCapabilityOverrides() {
        List<SysTenantCapabilityOverrideEntity> result = new ArrayList<>();
        List<SysTenantEntity> tenants = sysTenantMapper.selectList(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0)
                .orderByAsc(SysTenantEntity::getId));
        for (SysTenantEntity tenant : tenants) {
            if (!StringUtils.hasText(tenant.getTenantId())) {
                continue;
            }
            result.addAll(TenantContext.runWithTenant(tenant.getTenantId(), () -> sysTenantCapabilityOverrideMapper.selectList(
                    new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                            .eq(SysTenantCapabilityOverrideEntity::getTenantId, tenant.getTenantId())
            )));
        }
        return result;
    }

    private void ensureCapabilitiesExist(List<String> capabilityCodes) {
        for (String code : normalizedCodes(capabilityCodes)) {
            if (!capabilityExists(code, null)) {
                throw new BusinessException("能力编码不存在: " + code);
            }
        }
    }

    private void migratePackageReference(String oldCode, String newCode) {
        List<SysTenantEntity> tenants = sysTenantMapper.selectList(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0)
                .eq(SysTenantEntity::getPackageCode, oldCode));
        for (SysTenantEntity tenant : tenants) {
            tenant.setPackageCode(newCode);
            sysTenantMapper.updateById(tenant);
        }
        sysTenantPackageCapabilityMapper.delete(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                .eq(SysTenantPackageCapabilityEntity::getPackageCode, oldCode));
    }

    private void migrateCapabilityReference(String oldCode, String newCode) {
        List<SysTenantPackageCapabilityEntity> relations = sysTenantPackageCapabilityMapper.selectList(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                .eq(SysTenantPackageCapabilityEntity::getCapabilityCode, oldCode));
        for (SysTenantPackageCapabilityEntity relation : relations) {
            relation.setCapabilityCode(newCode);
            sysTenantPackageCapabilityMapper.updateById(relation);
        }
        for (SysTenantCapabilityOverrideEntity override : loadAllTenantCapabilityOverrides()) {
            if (!oldCode.equals(override.getCapabilityCode()) || !StringUtils.hasText(override.getTenantId())) {
                continue;
            }
            TenantContext.runWithTenant(override.getTenantId(), () -> {
                override.setCapabilityCode(newCode);
                sysTenantCapabilityOverrideMapper.updateById(override);
                return null;
            });
        }
        List<SysTenantCapabilityResourceScopeEntity> scopes = sysTenantCapabilityResourceScopeMapper.selectList(new LambdaQueryWrapper<SysTenantCapabilityResourceScopeEntity>()
                .eq(SysTenantCapabilityResourceScopeEntity::getTenantId, "platform")
                .eq(SysTenantCapabilityResourceScopeEntity::getCapabilityCode, oldCode));
        for (SysTenantCapabilityResourceScopeEntity scope : scopes) {
            scope.setCapabilityCode(newCode);
            sysTenantCapabilityResourceScopeMapper.updateById(scope);
        }
    }

    private boolean packageExists(String packageCode, Long excludeId) {
        String normalizedPackageCode = normalizeCode(packageCode);
        return sysTenantPackageMapper.selectCount(new LambdaQueryWrapper<SysTenantPackageEntity>()
                .eq(SysTenantPackageEntity::getTenantId, "platform")
                .eq(SysTenantPackageEntity::getPackageCode, normalizedPackageCode)
                .eq(SysTenantPackageEntity::getDeleted, 0)
                .ne(excludeId != null, SysTenantPackageEntity::getId, excludeId)) > 0;
    }

    private boolean capabilityExists(String capabilityCode, Long excludeId) {
        String normalizedCapabilityCode = normalizeCode(capabilityCode);
        return sysTenantCapabilityMapper.selectCount(new LambdaQueryWrapper<SysTenantCapabilityEntity>()
                .eq(SysTenantCapabilityEntity::getTenantId, "platform")
                .eq(SysTenantCapabilityEntity::getCapabilityCode, normalizedCapabilityCode)
                .eq(SysTenantCapabilityEntity::getDeleted, 0)
                .ne(excludeId != null, SysTenantCapabilityEntity::getId, excludeId)) > 0;
    }

    private SysTenantPackageEntity getPackage(Long id) {
        SysTenantPackageEntity entity = sysTenantPackageMapper.selectOne(new LambdaQueryWrapper<SysTenantPackageEntity>()
                .eq(SysTenantPackageEntity::getId, id)
                .eq(SysTenantPackageEntity::getTenantId, "platform")
                .eq(SysTenantPackageEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("租户套餐不存在");
        }
        return entity;
    }

    private SysTenantCapabilityEntity getCapability(Long id) {
        SysTenantCapabilityEntity entity = sysTenantCapabilityMapper.selectOne(new LambdaQueryWrapper<SysTenantCapabilityEntity>()
                .eq(SysTenantCapabilityEntity::getId, id)
                .eq(SysTenantCapabilityEntity::getTenantId, "platform")
                .eq(SysTenantCapabilityEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("租户能力不存在");
        }
        return entity;
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private void apply(SysTenantPackageEntity entity, TenantPackageCrudRequest request) {
        entity.setPackageCode(normalizeCode(request.packageCode()));
        entity.setPackageName(request.packageName().trim());
        entity.setSubtitle(blankToNull(request.subtitle()));
        entity.setSalesPrice(request.salesPrice());
        entity.setOriginalPrice(request.originalPrice());
        entity.setDescriptionMd(blankToNull(request.descriptionMd()));
        entity.setAppKey(blankToNull(request.appKey()));
        entity.setOrderNo(request.orderNo() == null ? 0 : request.orderNo());
        entity.setUserQuota(request.userQuota());
        entity.setStorageQuotaGb(request.storageQuotaGb());
        entity.setPackageDesc(blankToNull(request.packageDesc()));
        entity.setEnabled(request.enabled() == null || Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
    }

    private void apply(SysTenantCapabilityEntity entity, TenantCapabilityCrudRequest request) {
        entity.setCapabilityCode(normalizeCode(request.capabilityCode()));
        entity.setCapabilityName(request.capabilityName().trim());
        entity.setCapabilityDesc(StringUtils.hasText(request.capabilityDesc()) ? request.capabilityDesc().trim() : null);
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setEnabled(request.enabled() == null || Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
    }

    private List<String> normalizedCodes(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String normalized = value.trim();
            if (!result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private TenantPackageView toPackageView(
            SysTenantPackageEntity entity,
            List<String> capabilityCodes,
            PackageReferenceHint referenceHint
    ) {
        int referencedTenantCount = referenceHint == null ? 0 : referenceHint.referencedTenantCount();
        List<String> referencedTenantIds = referenceHint == null ? List.of() : referenceHint.sampleTenantIds();
        ResourceScopeSummary resourceScopeSummary = summarizeResourceScope(capabilityCodes);
        return new TenantPackageView(
                entity.getId(),
                entity.getPackageCode(),
                entity.getPackageName(),
                entity.getSubtitle(),
                entity.getSalesPrice(),
                entity.getOriginalPrice(),
                entity.getDescriptionMd(),
                entity.getAppKey(),
                entity.getOrderNo(),
                entity.getUserQuota(),
                entity.getStorageQuotaGb(),
                entity.getPackageDesc(),
                entity.getEnabled() == null || entity.getEnabled() == 1,
                capabilityCodes,
                resourceScopeSummary.visibleResourceCount(),
                resourceScopeSummary.grantResourceCount(),
                resourceScopeSummary.sampleResourceKeys(),
                TimeSupport.toEpochMilli(entity.getUpdatedAt() == null ? entity.getCreatedAt() : entity.getUpdatedAt()),
                referencedTenantCount,
                referencedTenantIds
        );
    }

    private TenantCapabilityView toCapabilityView(SysTenantCapabilityEntity entity, CapabilityReferenceHint referenceHint) {
        int referencedPackageCount = referenceHint == null ? 0 : referenceHint.referencedPackageCount();
        int referencedTenantCount = referenceHint == null ? 0 : referenceHint.referencedTenantCount();
        int overrideReferenceCount = referenceHint == null ? 0 : referenceHint.overrideReferenceCount();
        List<String> referencedPackageCodes = referenceHint == null ? List.of() : referenceHint.samplePackageCodes();
        List<String> referencedTenantIds = referenceHint == null ? List.of() : referenceHint.sampleTenantIds();
        return new TenantCapabilityView(
                entity.getId(),
                entity.getCapabilityCode(),
                entity.getCapabilityName(),
                entity.getCapabilityDesc(),
                entity.getSortOrder(),
                entity.getEnabled() == null || entity.getEnabled() == 1,
                TimeSupport.toEpochMilli(entity.getUpdatedAt() == null ? entity.getCreatedAt() : entity.getUpdatedAt()),
                referencedPackageCount,
                referencedPackageCodes,
                referencedTenantCount,
                referencedTenantIds,
                overrideReferenceCount
        );
    }

    private void requirePlatformTenant() {
        String tenantId = TenantContext.getTenantId();
        if (StringUtils.hasText(tenantId) && !"platform".equals(tenantId)) {
            throw new BusinessException("仅平台租户允许维护套餐与能力定义");
        }
    }

    @Schema(description = "租户套餐视图")
    public record TenantPackageView(
            @Schema(description = "主键 ID") Long id,
            @Schema(description = "套餐编码") String packageCode,
            @Schema(description = "套餐名称") String packageName,
            @Schema(description = "运营副标题") String subtitle,
            @Schema(description = "销售价") BigDecimal salesPrice,
            @Schema(description = "原价") BigDecimal originalPrice,
            @Schema(description = "富文本或 Markdown 描述") String descriptionMd,
            @Schema(description = "应用标识") String appKey,
            @Schema(description = "展示排序") Integer orderNo,
            @Schema(description = "用户配额") Integer userQuota,
            @Schema(description = "存储配额 GB") Integer storageQuotaGb,
            @Schema(description = "套餐说明") String packageDesc,
            @Schema(description = "是否启用") boolean enabled,
            @Schema(description = "能力编码集合") List<String> capabilityCodes,
            @Schema(description = "影响可见资源数量") int visibleResourceCount,
            @Schema(description = "影响授权资源数量") int grantResourceCount,
            @Schema(description = "影响资源示例（最多 8 条）") List<String> sampleResourceKeys,
            @Schema(description = "更新时间") Long updatedAt,
            @Schema(description = "引用该套餐的租户数量") int referencedTenantCount,
            @Schema(description = "引用该套餐的租户示例（最多 5 条）") List<String> referencedTenantIds
    ) {
    }

    @Schema(description = "租户能力视图")
    public record TenantCapabilityView(
            @Schema(description = "主键 ID") Long id,
            @Schema(description = "能力编码") String capabilityCode,
            @Schema(description = "能力名称") String capabilityName,
            @Schema(description = "能力说明") String capabilityDesc,
            @Schema(description = "排序值") Integer sortOrder,
            @Schema(description = "是否启用") boolean enabled,
            @Schema(description = "更新时间") Long updatedAt,
            @Schema(description = "引用该能力的套餐数量") int referencedPackageCount,
            @Schema(description = "引用该能力的套餐示例（最多 5 条）") List<String> referencedPackageCodes,
            @Schema(description = "通过套餐覆盖到的租户数量") int referencedTenantCount,
            @Schema(description = "通过套餐覆盖到的租户示例（最多 5 条）") List<String> referencedTenantIds,
            @Schema(description = "租户能力覆盖记录数量") int overrideReferenceCount
    ) {
    }

    @Schema(description = "套餐变更影响分析")
    public record TenantPackageImpactView(
            @Schema(description = "主键 ID") Long id,
            @Schema(description = "套餐编码") String packageCode,
            @Schema(description = "套餐名称") String packageName,
            @Schema(description = "是否启用") boolean enabled,
            @Schema(description = "能力编码集合") List<String> capabilityCodes,
            @Schema(description = "影响可见资源数量") int visibleResourceCount,
            @Schema(description = "影响授权资源数量") int grantResourceCount,
            @Schema(description = "影响资源示例（最多 8 条）") List<String> sampleResourceKeys,
            @Schema(description = "引用租户数量") int referencedTenantCount,
            @Schema(description = "引用租户示例（最多 5 条）") List<String> referencedTenantIds,
            @Schema(description = "命中的规则列表") List<ImpactRuleView> rules,
            @Schema(description = "建议操作") List<String> recommendedActions
    ) {
    }

    @Schema(description = "能力变更影响分析")
    public record TenantCapabilityImpactView(
            @Schema(description = "主键 ID") Long id,
            @Schema(description = "能力编码") String capabilityCode,
            @Schema(description = "能力名称") String capabilityName,
            @Schema(description = "是否启用") boolean enabled,
            @Schema(description = "引用套餐数量") int referencedPackageCount,
            @Schema(description = "引用套餐示例（最多 5 条）") List<String> referencedPackageCodes,
            @Schema(description = "关联租户数量（经套餐映射）") int referencedTenantCount,
            @Schema(description = "关联租户示例（最多 5 条）") List<String> referencedTenantIds,
            @Schema(description = "覆盖记录数量") int overrideReferenceCount,
            @Schema(description = "命中的规则列表") List<ImpactRuleView> rules,
            @Schema(description = "建议操作") List<String> recommendedActions
    ) {
    }

    @Schema(description = "影响规则命中结果")
    public record ImpactRuleView(
            @Schema(description = "规则编码") String ruleCode,
            @Schema(description = "规则等级：ERROR/WARN") String level,
            @Schema(description = "是否命中") boolean hit,
            @Schema(description = "规则说明") String message,
            @Schema(description = "关联数量") int relatedCount,
            @Schema(description = "是否阻断删除等高风险操作") boolean blocking
    ) {
    }

    private record PackageReferenceHint(int referencedTenantCount, List<String> sampleTenantIds) {
    }

    private record ResourceScopeSummary(int visibleResourceCount, int grantResourceCount, List<String> sampleResourceKeys) {
    }

    private record CapabilityReferenceHint(
            int referencedPackageCount,
            List<String> samplePackageCodes,
            int referencedTenantCount,
            List<String> sampleTenantIds,
            int overrideReferenceCount
    ) {
    }
}
