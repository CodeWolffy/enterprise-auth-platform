package com.enterprise.auth.platform.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysTenantCapabilityEntity;
import com.enterprise.auth.platform.persistence.entity.SysTenantCapabilityOverrideEntity;
import com.enterprise.auth.platform.persistence.entity.SysTenantEntity;
import com.enterprise.auth.platform.persistence.entity.SysTenantPackageCapabilityEntity;
import com.enterprise.auth.platform.persistence.entity.SysTenantPackageEntity;
import com.enterprise.auth.platform.persistence.mapper.SysTenantCapabilityMapper;
import com.enterprise.auth.platform.persistence.mapper.SysTenantCapabilityOverrideMapper;
import com.enterprise.auth.platform.persistence.mapper.SysTenantMapper;
import com.enterprise.auth.platform.persistence.mapper.SysTenantPackageCapabilityMapper;
import com.enterprise.auth.platform.persistence.mapper.SysTenantPackageMapper;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.tenant.dto.TenantCapabilityCrudRequest;
import com.enterprise.auth.platform.tenant.dto.TenantPackageCrudRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TenantCatalogManagementService {

    private final PersistenceProperties persistenceProperties;
    private final SysTenantPackageMapper sysTenantPackageMapper;
    private final SysTenantCapabilityMapper sysTenantCapabilityMapper;
    private final SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper;
    private final SysTenantMapper sysTenantMapper;
    private final SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper;
    private final AuditService auditService;

    public TenantCatalogManagementService(
            PersistenceProperties persistenceProperties,
            @Nullable SysTenantPackageMapper sysTenantPackageMapper,
            @Nullable SysTenantCapabilityMapper sysTenantCapabilityMapper,
            @Nullable SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper,
            @Nullable SysTenantMapper sysTenantMapper,
            @Nullable SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper,
            AuditService auditService
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysTenantPackageMapper = sysTenantPackageMapper;
        this.sysTenantCapabilityMapper = sysTenantCapabilityMapper;
        this.sysTenantPackageCapabilityMapper = sysTenantPackageCapabilityMapper;
        this.sysTenantMapper = sysTenantMapper;
        this.sysTenantCapabilityOverrideMapper = sysTenantCapabilityOverrideMapper;
        this.auditService = auditService;
    }

    public List<TenantPackageView> packages() {
        requirePlatformDatabaseMode();
        Map<String, List<String>> packageCapabilities = loadPackageCapabilities();
        Map<String, PackageReferenceHint> packageReferences = loadPackageReferences();
        return sysTenantPackageMapper.selectList(new LambdaQueryWrapper<SysTenantPackageEntity>()
                        .eq(SysTenantPackageEntity::getTenantId, "platform")
                        .eq(SysTenantPackageEntity::getDeleted, 0)
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
        requirePlatformDatabaseMode();
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

    @Transactional
    public TenantPackageView createPackage(TenantPackageCrudRequest request) {
        requirePlatformDatabaseMode();
        if (packageExists(request.packageCode(), null)) {
            throw new BusinessException("套餐编码已存在");
        }
        ensureCapabilitiesExist(request.capabilityCodes());
        SysTenantPackageEntity entity = new SysTenantPackageEntity();
        apply(entity, request);
        entity.setTenantId("platform");
        sysTenantPackageMapper.insert(entity);
        replacePackageCapabilities(request.packageCode(), request.capabilityCodes());
        auditService.record("TENANT_PACKAGE_CREATED", SecuritySupport.currentOperator(), "platform",
                java.util.Map.of("packageCode", request.packageCode()));
        return toPackageView(entity, normalizedCodes(request.capabilityCodes()), loadPackageReferences().get(request.packageCode()));
    }

    @Transactional
    public TenantPackageView updatePackage(Long id, TenantPackageCrudRequest request) {
        requirePlatformDatabaseMode();
        SysTenantPackageEntity entity = getPackage(id);
        if (packageExists(request.packageCode(), id)) {
            throw new BusinessException("套餐编码已存在");
        }
        ensureCapabilitiesExist(request.capabilityCodes());
        String oldCode = entity.getPackageCode();
        apply(entity, request);
        sysTenantPackageMapper.updateById(entity);
        if (!oldCode.equals(request.packageCode())) {
            sysTenantPackageCapabilityMapper.delete(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                    .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                    .eq(SysTenantPackageCapabilityEntity::getPackageCode, oldCode));
        }
        replacePackageCapabilities(request.packageCode(), request.capabilityCodes());
        auditService.record("TENANT_PACKAGE_UPDATED", SecuritySupport.currentOperator(), "platform",
                java.util.Map.of("packageId", id, "packageCode", request.packageCode()));
        return toPackageView(entity, normalizedCodes(request.capabilityCodes()), loadPackageReferences().get(request.packageCode()));
    }

    @Transactional
    public void deletePackage(Long id) {
        requirePlatformDatabaseMode();
        SysTenantPackageEntity entity = getPackage(id);
        PackageReferenceHint referenceHint = loadPackageReferences().get(entity.getPackageCode());
        if (referenceHint != null && referenceHint.referencedTenantCount() > 0) {
            throw new BusinessException("该套餐仍被租户使用，暂不允许删除");
        }
        sysTenantPackageMapper.deleteById(id);
        sysTenantPackageCapabilityMapper.delete(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                .eq(SysTenantPackageCapabilityEntity::getPackageCode, entity.getPackageCode()));
        auditService.record("TENANT_PACKAGE_DELETED", SecuritySupport.currentOperator(), "platform",
                java.util.Map.of("packageId", id, "packageCode", entity.getPackageCode()));
    }

    @Transactional
    public TenantCapabilityView createCapability(TenantCapabilityCrudRequest request) {
        requirePlatformDatabaseMode();
        if (capabilityExists(request.capabilityCode(), null)) {
            throw new BusinessException("能力编码已存在");
        }
        SysTenantCapabilityEntity entity = new SysTenantCapabilityEntity();
        apply(entity, request);
        entity.setTenantId("platform");
        sysTenantCapabilityMapper.insert(entity);
        auditService.record("TENANT_CAPABILITY_CREATED", SecuritySupport.currentOperator(), "platform",
                java.util.Map.of("capabilityCode", request.capabilityCode()));
        return toCapabilityView(entity, loadCapabilityReferences().get(request.capabilityCode()));
    }

    @Transactional
    public TenantCapabilityView updateCapability(Long id, TenantCapabilityCrudRequest request) {
        requirePlatformDatabaseMode();
        SysTenantCapabilityEntity entity = getCapability(id);
        if (capabilityExists(request.capabilityCode(), id)) {
            throw new BusinessException("能力编码已存在");
        }
        String oldCode = entity.getCapabilityCode();
        apply(entity, request);
        sysTenantCapabilityMapper.updateById(entity);
        if (!oldCode.equals(request.capabilityCode())) {
            migrateCapabilityReference(oldCode, request.capabilityCode());
        }
        auditService.record("TENANT_CAPABILITY_UPDATED", SecuritySupport.currentOperator(), "platform",
                java.util.Map.of("capabilityId", id, "capabilityCode", request.capabilityCode()));
        return toCapabilityView(entity, loadCapabilityReferences().get(request.capabilityCode()));
    }

    @Transactional
    public void deleteCapability(Long id) {
        requirePlatformDatabaseMode();
        SysTenantCapabilityEntity entity = getCapability(id);
        long packageRefCount = sysTenantPackageCapabilityMapper.selectCount(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                .eq(SysTenantPackageCapabilityEntity::getCapabilityCode, entity.getCapabilityCode()));
        if (packageRefCount > 0) {
            throw new BusinessException("该能力仍被套餐引用，暂不允许删除");
        }
        long overrideRefCount = sysTenantCapabilityOverrideMapper == null ? 0
                : sysTenantCapabilityOverrideMapper.selectCount(new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                .eq(SysTenantCapabilityOverrideEntity::getCapabilityCode, entity.getCapabilityCode()));
        if (overrideRefCount > 0) {
            throw new BusinessException("该能力存在租户覆盖记录，暂不允许删除");
        }
        sysTenantCapabilityMapper.deleteById(id);
        auditService.record("TENANT_CAPABILITY_DELETED", SecuritySupport.currentOperator(), "platform",
                java.util.Map.of("capabilityId", id, "capabilityCode", entity.getCapabilityCode()));
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
        if (sysTenantMapper == null) {
            return Map.of();
        }
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
        if (sysTenantMapper != null) {
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
        }

        Map<String, Integer> overrideRefCounts = new LinkedHashMap<>();
        if (sysTenantCapabilityOverrideMapper != null) {
            for (SysTenantCapabilityOverrideEntity override : sysTenantCapabilityOverrideMapper.selectList(new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>())) {
                if (!StringUtils.hasText(override.getCapabilityCode())) {
                    continue;
                }
                String code = override.getCapabilityCode().trim();
                overrideRefCounts.put(code, overrideRefCounts.getOrDefault(code, 0) + 1);
            }
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

    private void ensureCapabilitiesExist(List<String> capabilityCodes) {
        for (String code : normalizedCodes(capabilityCodes)) {
            if (!capabilityExists(code, null)) {
                throw new BusinessException("能力编码不存在: " + code);
            }
        }
    }

    private void migrateCapabilityReference(String oldCode, String newCode) {
        List<SysTenantPackageCapabilityEntity> relations = sysTenantPackageCapabilityMapper.selectList(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                .eq(SysTenantPackageCapabilityEntity::getCapabilityCode, oldCode));
        for (SysTenantPackageCapabilityEntity relation : relations) {
            relation.setCapabilityCode(newCode);
            sysTenantPackageCapabilityMapper.updateById(relation);
        }
        if (sysTenantCapabilityOverrideMapper != null) {
            List<SysTenantCapabilityOverrideEntity> overrides = sysTenantCapabilityOverrideMapper.selectList(new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                    .eq(SysTenantCapabilityOverrideEntity::getCapabilityCode, oldCode));
            for (SysTenantCapabilityOverrideEntity override : overrides) {
                override.setCapabilityCode(newCode);
                sysTenantCapabilityOverrideMapper.updateById(override);
            }
        }
    }

    private boolean packageExists(String packageCode, Long excludeId) {
        return sysTenantPackageMapper.selectCount(new LambdaQueryWrapper<SysTenantPackageEntity>()
                .eq(SysTenantPackageEntity::getTenantId, "platform")
                .eq(SysTenantPackageEntity::getPackageCode, packageCode)
                .eq(SysTenantPackageEntity::getDeleted, 0)
                .ne(excludeId != null, SysTenantPackageEntity::getId, excludeId)) > 0;
    }

    private boolean capabilityExists(String capabilityCode, Long excludeId) {
        return sysTenantCapabilityMapper.selectCount(new LambdaQueryWrapper<SysTenantCapabilityEntity>()
                .eq(SysTenantCapabilityEntity::getTenantId, "platform")
                .eq(SysTenantCapabilityEntity::getCapabilityCode, capabilityCode)
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

    private void apply(SysTenantPackageEntity entity, TenantPackageCrudRequest request) {
        entity.setPackageCode(request.packageCode().trim());
        entity.setPackageName(request.packageName().trim());
        entity.setUserQuota(request.userQuota());
        entity.setStorageQuotaGb(request.storageQuotaGb());
        entity.setPackageDesc(StringUtils.hasText(request.packageDesc()) ? request.packageDesc().trim() : null);
        entity.setEnabled(request.enabled() == null || Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
    }

    private void apply(SysTenantCapabilityEntity entity, TenantCapabilityCrudRequest request) {
        entity.setCapabilityCode(request.capabilityCode().trim());
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

    private TenantPackageView toPackageView(
            SysTenantPackageEntity entity,
            List<String> capabilityCodes,
            @Nullable PackageReferenceHint referenceHint
    ) {
        int referencedTenantCount = referenceHint == null ? 0 : referenceHint.referencedTenantCount();
        List<String> referencedTenantIds = referenceHint == null ? List.of() : referenceHint.sampleTenantIds();
        return new TenantPackageView(
                entity.getId(),
                entity.getPackageCode(),
                entity.getPackageName(),
                entity.getUserQuota(),
                entity.getStorageQuotaGb(),
                entity.getPackageDesc(),
                entity.getEnabled() == null || entity.getEnabled() == 1,
                capabilityCodes,
                entity.getUpdatedAt() == null ? entity.getCreatedAt() : entity.getUpdatedAt(),
                referencedTenantCount,
                referencedTenantIds
        );
    }

    private TenantCapabilityView toCapabilityView(SysTenantCapabilityEntity entity, @Nullable CapabilityReferenceHint referenceHint) {
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
                entity.getUpdatedAt() == null ? entity.getCreatedAt() : entity.getUpdatedAt(),
                referencedPackageCount,
                referencedPackageCodes,
                referencedTenantCount,
                referencedTenantIds,
                overrideReferenceCount
        );
    }

    private void requirePlatformDatabaseMode() {
        if (!persistenceProperties.databaseEnabled()
                || sysTenantPackageMapper == null
                || sysTenantCapabilityMapper == null
                || sysTenantPackageCapabilityMapper == null) {
            throw new BusinessException("当前未启用数据库租户目录管理能力");
        }
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
            @Schema(description = "用户配额") Integer userQuota,
            @Schema(description = "存储配额 GB") Integer storageQuotaGb,
            @Schema(description = "套餐说明") String packageDesc,
            @Schema(description = "是否启用") boolean enabled,
            @Schema(description = "能力编码集合") List<String> capabilityCodes,
            @Schema(description = "更新时间") LocalDateTime updatedAt,
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
            @Schema(description = "更新时间") LocalDateTime updatedAt,
            @Schema(description = "引用该能力的套餐数量") int referencedPackageCount,
            @Schema(description = "引用该能力的套餐示例（最多 5 条）") List<String> referencedPackageCodes,
            @Schema(description = "通过套餐覆盖到的租户数量") int referencedTenantCount,
            @Schema(description = "通过套餐覆盖到的租户示例（最多 5 条）") List<String> referencedTenantIds,
            @Schema(description = "租户能力覆盖记录数量") int overrideReferenceCount
    ) {
    }

    private record PackageReferenceHint(int referencedTenantCount, List<String> sampleTenantIds) {
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
