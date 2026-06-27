package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantPackageEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantPackageMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TenantProfileFacade {

    private final SysTenantMapper sysTenantMapper;
    private final SysTenantPackageMapper sysTenantPackageMapper;

    public TenantProfileFacade(
            SysTenantMapper sysTenantMapper,
            SysTenantPackageMapper sysTenantPackageMapper
    ) {
        this.sysTenantMapper = sysTenantMapper;
        this.sysTenantPackageMapper = sysTenantPackageMapper;
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

    public void ensureTenantAccessible(String tenantId) {
        SysTenantEntity tenant = findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "租户不存在"));
        ensureTenantAccessible(tenant);
    }

    public void ensureTenantAccessible(SysTenantEntity tenant) {
        if (tenant.getTenantStatus() == null || tenant.getTenantStatus() != 1) {
            throw new BusinessException("TENANT_DISABLED", "租户已停用");
        }
        java.time.LocalDateTime now = TimeSupport.utcNowDateTime();
        if (tenant.getAuthBeginAt() != null && tenant.getAuthBeginAt().isAfter(now)) {
            throw new BusinessException("TENANT_DISABLED", "租户授权尚未生效");
        }
        if (tenant.getExpireAt() != null && !tenant.getExpireAt().isAfter(now)) {
            throw new BusinessException("TENANT_DISABLED", "租户授权已过期");
        }
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

    public List<TenantRecord> listTenantRecords() {
        return listTenants().stream()
                .map(t -> new TenantRecord(t.getTenantId(), t.getTenantName(),
                        t.getPlatformLevel(), t.getTenantStatus(),
                        t.getAuthBeginAt(), t.getExpireAt(),
                        t.getLogoUrl(), t.getContactName(), t.getContactPhone(), t.getContactEmail(),
                        t.getWebsite(), t.getAddress(),
                        t.getLifecycleNote(), t.getPackageCode()))
                .toList();
    }

    public Map<String, PackageRecord> loadPackageRecords(List<String> packageCodes) {
        return loadPackages(packageCodes).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            SysTenantPackageEntity p = e.getValue();
                            return new PackageRecord(p.getPackageCode(), p.getPackageName());
                        },
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    public record TenantRecord(String tenantId, String tenantName, Integer platformLevel, Integer tenantStatus,
                               java.time.LocalDateTime authBeginAt, java.time.LocalDateTime expireAt,
                               String logoUrl, String contactName, String contactPhone, String contactEmail,
                               String website, String address,
                               String lifecycleNote, String packageCode) {}

    public record PackageRecord(String packageCode, String packageName) {}
}
