package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantCapabilityEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantCapabilityOverrideEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantPackageCapabilityEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantPackageEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantCapabilityMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantCapabilityOverrideMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantPackageCapabilityMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantPackageMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TenantProfileFacade {

    private final SysTenantMapper sysTenantMapper;
    private final SysTenantPackageMapper sysTenantPackageMapper;
    private final SysTenantCapabilityMapper sysTenantCapabilityMapper;
    private final SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper;
    private final SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper;

    public TenantProfileFacade(
            SysTenantMapper sysTenantMapper,
            SysTenantPackageMapper sysTenantPackageMapper,
            SysTenantCapabilityMapper sysTenantCapabilityMapper,
            SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper,
            SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper
    ) {
        this.sysTenantMapper = sysTenantMapper;
        this.sysTenantPackageMapper = sysTenantPackageMapper;
        this.sysTenantCapabilityMapper = sysTenantCapabilityMapper;
        this.sysTenantPackageCapabilityMapper = sysTenantPackageCapabilityMapper;
        this.sysTenantCapabilityOverrideMapper = sysTenantCapabilityOverrideMapper;
    }

    public List<SysTenantEntity> listTenants() {
        return sysTenantMapper.selectList(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0)
                .orderByAsc(SysTenantEntity::getId));
    }

    public Optional<SysTenantEntity> findByTenantId(String tenantId) {
        SysTenantEntity entity = sysTenantMapper.selectOne(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getTenantId, tenantId)
                .eq(SysTenantEntity::getDeleted, 0)
                .last("limit 1"));
        return Optional.ofNullable(entity);
    }

    public Map<String, SysTenantPackageEntity> loadPackages(List<String> packageCodes) {
        if (packageCodes == null || packageCodes.isEmpty()) {
            return Map.of();
        }
        return sysTenantPackageMapper.selectList(new LambdaQueryWrapper<SysTenantPackageEntity>()
                        .eq(SysTenantPackageEntity::getTenantId, "platform")
                        .eq(SysTenantPackageEntity::getDeleted, 0)
                        .in(SysTenantPackageEntity::getPackageCode, packageCodes))
                .stream().collect(Collectors.toMap(
                        SysTenantPackageEntity::getPackageCode,
                        Function.identity(),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    public Map<String, SysTenantCapabilityEntity> loadCapabilities() {
        return sysTenantCapabilityMapper.selectList(new LambdaQueryWrapper<SysTenantCapabilityEntity>()
                        .eq(SysTenantCapabilityEntity::getTenantId, "platform")
                        .eq(SysTenantCapabilityEntity::getDeleted, 0)
                        .eq(SysTenantCapabilityEntity::getEnabled, 1)
                        .orderByAsc(SysTenantCapabilityEntity::getSortOrder)
                        .orderByAsc(SysTenantCapabilityEntity::getId))
                .stream().collect(Collectors.toMap(
                        SysTenantCapabilityEntity::getCapabilityCode,
                        Function.identity(),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    public Map<String, List<String>> loadPackageCapabilities(List<String> packageCodes) {
        if (packageCodes == null || packageCodes.isEmpty()) {
            return Map.of();
        }
        return sysTenantPackageCapabilityMapper.selectList(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                        .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                        .in(SysTenantPackageCapabilityEntity::getPackageCode, packageCodes))
                .stream().collect(Collectors.groupingBy(
                        SysTenantPackageCapabilityEntity::getPackageCode,
                        LinkedHashMap::new,
                        Collectors.mapping(SysTenantPackageCapabilityEntity::getCapabilityCode, Collectors.toList())
                ));
    }

    public Map<String, List<SysTenantCapabilityOverrideEntity>> loadOverrides(List<String> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return Map.of();
        }
        return sysTenantCapabilityOverrideMapper.selectList(new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                        .in(SysTenantCapabilityOverrideEntity::getTenantId, tenantIds))
                .stream().collect(Collectors.groupingBy(
                        SysTenantCapabilityOverrideEntity::getTenantId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    public String capabilityDescription(SysTenantCapabilityEntity capability) {
        if (capability == null || !StringUtils.hasText(capability.getCapabilityDesc())) {
            return "该能力已启用，可在租户侧使用对应模块。";
        }
        return capability.getCapabilityDesc();
    }

    // Projection methods for cross-module use (no infrastructure entity exposure)

    public List<TenantRecord> listTenantRecords() {
        return listTenants().stream()
                .map(t -> new TenantRecord(t.getTenantId(), t.getTenantName(),
                        t.getPlatformLevel(), t.getTenantStatus(),
                        t.getExpireAt(), t.getLifecycleNote(), t.getPackageCode()))
                .toList();
    }

    public Map<String, PackageRecord> loadPackageRecords(List<String> packageCodes) {
        return loadPackages(packageCodes).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            SysTenantPackageEntity p = e.getValue();
                            return new PackageRecord(p.getPackageCode(), p.getPackageName(),
                                    p.getUserQuota(), p.getStorageQuotaGb());
                        },
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    public Map<String, CapabilityRecord> loadCapabilityRecords() {
        return loadCapabilities().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            SysTenantCapabilityEntity c = e.getValue();
                            return new CapabilityRecord(c.getCapabilityCode(), c.getCapabilityDesc());
                        },
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    public Map<String, List<OverrideRecord>> loadOverrideRecords(List<String> tenantIds) {
        return loadOverrides(tenantIds).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(o -> new OverrideRecord(o.getTenantId(), o.getCapabilityCode(),
                                        o.getEnabled(), o.getCapabilityDescOverride()))
                                .toList(),
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    public String capabilityDescription(CapabilityRecord capability) {
        if (capability == null || !StringUtils.hasText(capability.capabilityDesc())) {
            return "该能力已启用，可在租户侧使用对应模块。";
        }
        return capability.capabilityDesc();
    }

    public record TenantRecord(String tenantId, String tenantName, Integer platformLevel, Integer tenantStatus,
                               java.time.LocalDateTime expireAt, String lifecycleNote, String packageCode) {}

    public record PackageRecord(String packageCode, String packageName, Integer userQuota, Integer storageQuotaGb) {}

    public record CapabilityRecord(String capabilityCode, String capabilityDesc) {}

    public record OverrideRecord(String tenantId, String capabilityCode, Integer enabled, String capabilityDescOverride) {}
}