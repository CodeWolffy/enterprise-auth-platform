package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import com.enterprise.auth.platform.modules.dept.application.DeptTenantDataFacade;
import com.enterprise.auth.platform.modules.resource.application.CatalogService;
import com.enterprise.auth.platform.modules.role.application.RoleTenantDataFacade;
import com.enterprise.auth.platform.modules.security.application.SecurityPolicyApplicationService;
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
import com.enterprise.auth.platform.modules.user.application.UserTenantDataFacade;
import com.enterprise.auth.platform.modules.tenant.application.TenantAccessPolicy;
import com.enterprise.auth.platform.modules.tenant.application.TenantChangeLogApplicationService;
import com.enterprise.auth.platform.common.authz.SecuritySupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.tenant.interfaces.CreateTenantRequest;
import com.enterprise.auth.platform.modules.tenant.interfaces.UpdateTenantCapabilityOverridesRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TenantManagementService {

    private final SysTenantMapper sysTenantMapper;
    private final UserTenantDataFacade userTenantDataFacade;
    private final RoleTenantDataFacade roleTenantDataFacade;
    private final DeptTenantDataFacade deptTenantDataFacade;
    private final SysTenantPackageMapper sysTenantPackageMapper;
    private final SysTenantCapabilityMapper sysTenantCapabilityMapper;
    private final SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper;
    private final SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper;
    private final CatalogService catalogService;
    private final AuditService auditService;
    private final TenantAccessPolicy tenantAccessPolicy;
    private final TenantChangeLogApplicationService tenantChangeLogApplicationService;
    private final SecurityPolicyApplicationService securityPolicyApplicationService;

    public TenantManagementService(
            SysTenantMapper sysTenantMapper,
            UserTenantDataFacade userTenantDataFacade,
            RoleTenantDataFacade roleTenantDataFacade,
            DeptTenantDataFacade deptTenantDataFacade,
            SysTenantPackageMapper sysTenantPackageMapper,
            SysTenantCapabilityMapper sysTenantCapabilityMapper,
            SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper,
            SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper,
            CatalogService catalogService,
            AuditService auditService,
            TenantAccessPolicy tenantAccessPolicy,
            TenantChangeLogApplicationService tenantChangeLogApplicationService,
            SecurityPolicyApplicationService securityPolicyApplicationService
    ) {
        this.sysTenantMapper = sysTenantMapper;
        this.userTenantDataFacade = userTenantDataFacade;
        this.roleTenantDataFacade = roleTenantDataFacade;
        this.deptTenantDataFacade = deptTenantDataFacade;
        this.sysTenantPackageMapper = sysTenantPackageMapper;
        this.sysTenantCapabilityMapper = sysTenantCapabilityMapper;
        this.sysTenantPackageCapabilityMapper = sysTenantPackageCapabilityMapper;
        this.sysTenantCapabilityOverrideMapper = sysTenantCapabilityOverrideMapper;
        this.catalogService = catalogService;
        this.auditService = auditService;
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.tenantChangeLogApplicationService = tenantChangeLogApplicationService;
        this.securityPolicyApplicationService = securityPolicyApplicationService;
    }

    @Transactional
    public CatalogService.TenantView create(CreateTenantRequest request) {
        requirePlatformSuperAdmin();
        String operator = SecuritySupport.currentOperator();
        if (existsTenant(request.tenantId())) {
            throw new BusinessException("租户标识已存在");
        }

        SysTenantEntity entity = new SysTenantEntity();
        entity.setTenantId(request.tenantId());
        entity.setTenantName(request.tenantName());
        entity.setPlatformLevel(request.platformLevel() ? 1 : 0);
        entity.setTenantStatus(request.tenantStatus() == null ? 1 : request.tenantStatus());
        entity.setExpireAt(TimeSupport.localDateTimeFromEpochMilli(request.expireAt()));
        entity.setPackageCode(request.packageCode());
        entity.setLifecycleNote(request.lifecycleNote());
        sysTenantMapper.insert(entity);
        securityPolicyApplicationService.ensureTenantPolicy(request.tenantId());
        saveTenantProfile(request.tenantId(), request.packageCode(), request.packageName(), request.userQuota(),
                request.storageQuotaGb(), request.capabilityCodes());
        recordTenantChange(request.tenantId(), "CREATED", "tenant", null, request.tenantName(), "创建租户", operator);
        recordTenantChange(request.tenantId(), "STATUS", "tenantStatus", null,
                String.valueOf(entity.getTenantStatus()), "初始化租户状态", operator);
        recordTenantChange(request.tenantId(), "PACKAGE", "packageCode", null,
                request.packageCode(), "初始化租户套餐", operator);
        recordTenantChange(request.tenantId(), "PROFILE", "lifecycleNote", null, request.lifecycleNote(), "初始化运营备注", operator);

        auditService.record("TENANT_CREATED", operator, request.tenantId(), Map.of("tenantId", request.tenantId()));
        return catalogService.tenant(request.tenantId());
    }

    @Transactional
    public CatalogService.TenantView update(String tenantId, CreateTenantRequest request) {
        requirePlatformSuperAdmin();
        SysTenantEntity entity = getTenant(tenantId);
        String oldTenantName = entity.getTenantName();
        Integer oldTenantStatus = entity.getTenantStatus();
        java.time.LocalDateTime oldExpireAt = entity.getExpireAt();
        TenantProfile oldProfile = loadTenantProfiles(List.of(entity)).getOrDefault(tenantId, TenantProfile.empty());
        entity.setTenantName(request.tenantName());
        entity.setPlatformLevel(request.platformLevel() ? 1 : 0);
        if (request.tenantStatus() != null) {
            entity.setTenantStatus(request.tenantStatus());
        }
        entity.setExpireAt(TimeSupport.localDateTimeFromEpochMilli(request.expireAt()));
        entity.setPackageCode(request.packageCode());
        entity.setLifecycleNote(request.lifecycleNote());
        sysTenantMapper.updateById(entity);
        saveTenantProfile(tenantId, request.packageCode(), request.packageName(), request.userQuota(),
                request.storageQuotaGb(), request.capabilityCodes());

        String operator = SecuritySupport.currentOperator();
        recordIfChanged(tenantId, "PROFILE", "tenantName", oldTenantName, request.tenantName(), "更新租户名称", operator);
        recordIfChanged(tenantId, "STATUS", "tenantStatus", toStringValue(oldTenantStatus), toStringValue(entity.getTenantStatus()), "更新租户状态", operator);
        recordIfChanged(tenantId, "PROFILE", "expireAt", toStringValue(oldExpireAt), toStringValue(request.expireAt()), "更新到期时间", operator);
        recordIfChanged(tenantId, "PACKAGE", "packageCode", oldProfile.packageCode(), request.packageCode(), "更新租户套餐编码", operator);
        recordIfChanged(tenantId, "PACKAGE", "packageName", oldProfile.packageName(), request.packageName(), "更新租户套餐名称", operator);
        recordIfChanged(tenantId, "PACKAGE", "userQuota", toStringValue(oldProfile.userQuota()), toStringValue(request.userQuota()), "更新用户配额", operator);
        recordIfChanged(tenantId, "PACKAGE", "storageQuotaGb", toStringValue(oldProfile.storageQuotaGb()), toStringValue(request.storageQuotaGb()), "更新存储配额", operator);
        recordIfChanged(tenantId, "CAPABILITY", "capabilityCodes", oldProfile.capabilityCodes().isEmpty() ? null : String.join(",", oldProfile.capabilityCodes()),
                request.capabilityCodes() == null ? null : String.join(",", request.capabilityCodes()), "更新租户能力范围", operator);
        recordIfChanged(tenantId, "PROFILE", "lifecycleNote", oldProfile.lifecycleNote(), request.lifecycleNote(), "更新运营备注", operator);

        auditService.record("TENANT_UPDATED", operator, tenantId, Map.of("tenantId", tenantId));
        return catalogService.tenant(tenantId);
    }

    @Transactional
    public void delete(String tenantId) {
        requirePlatformSuperAdmin();
        String operator = SecuritySupport.currentOperator();
        SysTenantEntity entity = getTenant(tenantId);
        boolean tenantHasRelatedData = userTenantDataFacade.hasActiveUsers(tenantId)
                || roleTenantDataFacade.hasActiveRoles(tenantId)
                || deptTenantDataFacade.hasActiveDepartments(tenantId);
        if (tenantHasRelatedData) {
            throw new BusinessException("租户下仍存在用户、角色或部门数据，暂不允许删除");
        }

        sysTenantMapper.deleteById(entity.getId());
        sysTenantCapabilityOverrideMapper.delete(new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                .eq(SysTenantCapabilityOverrideEntity::getTenantId, tenantId));
        recordTenantChange(tenantId, "DELETED", "tenant", entity.getTenantName(), null, "删除租户", operator);
        auditService.record("TENANT_DELETED", operator, tenantId, Map.of("tenantId", entity.getTenantId()));
    }

    public PageResult<CatalogService.TenantView> page(String keyword, Boolean platformLevel, Integer tenantStatus, int page, int size) {
        boolean platformSuperAdmin = isPlatformSuperAdmin();
        String operatorTenantId = currentTenantId();
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        LambdaQueryWrapper<SysTenantEntity> query = new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0)
                .eq(!platformSuperAdmin, SysTenantEntity::getTenantId, operatorTenantId)
                .eq(platformLevel != null, SysTenantEntity::getPlatformLevel, Boolean.TRUE.equals(platformLevel) ? 1 : 0)
                .eq(tenantStatus != null, SysTenantEntity::getTenantStatus, tenantStatus)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(SysTenantEntity::getTenantId, keyword)
                        .or()
                        .like(SysTenantEntity::getTenantName, keyword));
        long total = sysTenantMapper.selectCount(query);
        if (total == 0) {
            return PageResult.of(0, safePage, safeSize, List.of());
        }
        int offset = (safePage - 1) * safeSize;
        List<SysTenantEntity> entities = sysTenantMapper.selectList(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0)
                .eq(!platformSuperAdmin, SysTenantEntity::getTenantId, operatorTenantId)
                .eq(platformLevel != null, SysTenantEntity::getPlatformLevel, Boolean.TRUE.equals(platformLevel) ? 1 : 0)
                .eq(tenantStatus != null, SysTenantEntity::getTenantStatus, tenantStatus)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(SysTenantEntity::getTenantId, keyword)
                        .or()
                        .like(SysTenantEntity::getTenantName, keyword))
                .orderByDesc(SysTenantEntity::getCreatedAt)
                .orderByDesc(SysTenantEntity::getId)
                .last("limit " + offset + "," + safeSize));
        Map<String, TenantProfile> profiles = loadTenantProfiles(entities);
        List<CatalogService.TenantView> records = entities.stream()
                .map(entity -> toTenantView(entity, profiles.getOrDefault(entity.getTenantId(), TenantProfile.empty())))
                .toList();
        return PageResult.of(total, safePage, safeSize, records);
    }

    public TenantCapabilityOverrideView capabilityOverrides(String tenantId) {
        ensureTenantReadable(tenantId);
        SysTenantEntity tenant = getTenant(tenantId);
        Map<String, TenantProfile> profiles = loadTenantProfiles(List.of(tenant));
        TenantProfile profile = profiles.getOrDefault(tenantId, TenantProfile.empty());
        return buildCapabilityOverrideView(tenant, profile);
    }

    @Transactional
    public TenantCapabilityOverrideView updateCapabilityOverrides(
            String tenantId,
            UpdateTenantCapabilityOverridesRequest request
    ) {
        ensureTenantReadable(tenantId);
        SysTenantEntity tenant = getTenant(tenantId);
        TenantCapabilityOverrideView before = capabilityOverrides(tenantId);
        saveTenantCapabilityOverridesFromRequest(tenantId, tenant.getPackageCode(), request == null ? List.of() : request.overrides());
        String operator = SecuritySupport.currentOperator();
        recordIfChanged(
                tenantId,
                "CAPABILITY",
                "capabilityOverrides",
                summarizeOverrides(before.overrides()),
                summarizeOverrides(capabilityOverrides(tenantId).overrides()),
                "更新租户能力覆盖",
                operator
        );
        auditService.record("TENANT_CAPABILITY_OVERRIDES_UPDATED", operator, tenantId, Map.of("tenantId", tenantId));
        return capabilityOverrides(tenantId);
    }

    private boolean existsTenant(String tenantId) {
        return sysTenantMapper.selectCount(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getTenantId, tenantId)
                .eq(SysTenantEntity::getDeleted, 0)) > 0;
    }

    private SysTenantEntity getTenant(String tenantId) {
        SysTenantEntity entity = sysTenantMapper.selectOne(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getTenantId, tenantId)
                .eq(SysTenantEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("租户不存在");
        }
        return entity;
    }

    private void saveTenantProfile(
            String tenantId,
            String packageCode,
            String packageName,
            Integer userQuota,
            Integer storageQuotaGb,
            java.util.List<String> capabilityCodes
    ) {
        if (StringUtils.hasText(packageCode)) {
            upsertPackageDefinition(packageCode, packageName, userQuota, storageQuotaGb, capabilityCodes);
        }
        saveTenantCapabilityOverrides(tenantId, packageCode, capabilityCodes);
    }

    private Map<String, TenantProfile> loadTenantProfiles(List<SysTenantEntity> tenants) {
        if (tenants.isEmpty()) {
            return Map.of();
        }
        List<String> packageCodes = tenants.stream()
                .map(SysTenantEntity::getPackageCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, SysTenantPackageEntity> packages = withPlatformTenant(() -> packageCodes.isEmpty() ? Map.<String, SysTenantPackageEntity>of() : sysTenantPackageMapper.selectList(
                new LambdaQueryWrapper<SysTenantPackageEntity>()
                        .eq(SysTenantPackageEntity::getTenantId, "platform")
                        .eq(SysTenantPackageEntity::getDeleted, 0)
                        .in(SysTenantPackageEntity::getPackageCode, packageCodes)
        ).stream().collect(java.util.stream.Collectors.toMap(
                SysTenantPackageEntity::getPackageCode,
                java.util.function.Function.identity(),
                (left, right) -> right,
                java.util.LinkedHashMap::new
        )));
        Map<String, SysTenantCapabilityEntity> capabilities = withPlatformTenant(() -> sysTenantCapabilityMapper.selectList(
                new LambdaQueryWrapper<SysTenantCapabilityEntity>()
                        .eq(SysTenantCapabilityEntity::getTenantId, "platform")
                        .eq(SysTenantCapabilityEntity::getDeleted, 0)
                        .eq(SysTenantCapabilityEntity::getEnabled, 1)
                        .orderByAsc(SysTenantCapabilityEntity::getSortOrder)
                        .orderByAsc(SysTenantCapabilityEntity::getId)
        ).stream().collect(java.util.stream.Collectors.toMap(
                SysTenantCapabilityEntity::getCapabilityCode,
                java.util.function.Function.identity(),
                (left, right) -> right,
                java.util.LinkedHashMap::new
        )));
        Map<String, List<String>> packageCapabilities = withPlatformTenant(() -> packageCodes.isEmpty() ? Map.<String, List<String>>of() : sysTenantPackageCapabilityMapper.selectList(
                new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                        .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                        .in(SysTenantPackageCapabilityEntity::getPackageCode, packageCodes)
        ).stream().collect(java.util.stream.Collectors.groupingBy(
                SysTenantPackageCapabilityEntity::getPackageCode,
                java.util.LinkedHashMap::new,
                java.util.stream.Collectors.mapping(SysTenantPackageCapabilityEntity::getCapabilityCode, java.util.stream.Collectors.toList())
        )));
        Map<String, List<SysTenantCapabilityOverrideEntity>> overrides = sysTenantCapabilityOverrideMapper.selectList(
                new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                        .in(SysTenantCapabilityOverrideEntity::getTenantId, tenants.stream().map(SysTenantEntity::getTenantId).toList())
        ).stream().collect(java.util.stream.Collectors.groupingBy(
                SysTenantCapabilityOverrideEntity::getTenantId,
                java.util.LinkedHashMap::new,
                java.util.stream.Collectors.toList()
        ));
        Map<String, TenantProfile> result = new java.util.LinkedHashMap<>();
        for (SysTenantEntity tenant : tenants) {
            SysTenantPackageEntity pkg = packages.get(tenant.getPackageCode());
            List<String> capabilityCodes = new java.util.ArrayList<>(packageCapabilities.getOrDefault(tenant.getPackageCode(), List.of()));
            Map<String, String> descriptions = new java.util.LinkedHashMap<>();
            for (String code : capabilityCodes) {
                descriptions.put(code, capabilityDescription(capabilities.get(code)));
            }
            for (SysTenantCapabilityOverrideEntity override : overrides.getOrDefault(tenant.getTenantId(), List.of())) {
                if (override.getEnabled() != null && override.getEnabled() == 1) {
                    if (!capabilityCodes.contains(override.getCapabilityCode())) {
                        capabilityCodes.add(override.getCapabilityCode());
                    }
                    descriptions.put(override.getCapabilityCode(),
                            StringUtils.hasText(override.getCapabilityDescOverride())
                                    ? override.getCapabilityDescOverride()
                                    : capabilityDescription(capabilities.get(override.getCapabilityCode())));
                } else {
                    capabilityCodes.remove(override.getCapabilityCode());
                    descriptions.remove(override.getCapabilityCode());
                }
            }
            result.put(tenant.getTenantId(), new TenantProfile(
                    tenant.getPackageCode(),
                    pkg == null ? null : pkg.getPackageName(),
                    pkg == null ? null : pkg.getUserQuota(),
                    pkg == null ? null : pkg.getStorageQuotaGb(),
                    capabilityCodes,
                    descriptions,
                    tenant.getLifecycleNote()
            ));
        }
        return result;
    }

    private CatalogService.TenantView toTenantView(SysTenantEntity tenant, TenantProfile profile) {
        return new CatalogService.TenantView(
                tenant.getTenantId(),
                tenant.getTenantName(),
                tenant.getPlatformLevel() != null && tenant.getPlatformLevel() == 1,
                tenant.getTenantStatus(),
                TimeSupport.toEpochMilli(tenant.getExpireAt()),
                profile.packageCode(),
                profile.packageName(),
                profile.userQuota(),
                profile.storageQuotaGb(),
                profile.capabilityCodes(),
                profile.capabilityDescriptions(),
                profile.lifecycleNote()
        );
    }

    private TenantCapabilityOverrideView buildCapabilityOverrideView(SysTenantEntity tenant, TenantProfile profile) {
        Map<String, SysTenantCapabilityEntity> capabilityMap = withPlatformTenant(() -> sysTenantCapabilityMapper.selectList(
                new LambdaQueryWrapper<SysTenantCapabilityEntity>()
                        .eq(SysTenantCapabilityEntity::getTenantId, "platform")
                        .eq(SysTenantCapabilityEntity::getDeleted, 0)
                        .orderByAsc(SysTenantCapabilityEntity::getSortOrder)
                        .orderByAsc(SysTenantCapabilityEntity::getId)
        ).stream().collect(java.util.stream.Collectors.toMap(
                SysTenantCapabilityEntity::getCapabilityCode,
                java.util.function.Function.identity(),
                (left, right) -> right,
                java.util.LinkedHashMap::new
        )));
        List<String> packageCapabilityCodes = withPlatformTenant(() -> StringUtils.hasText(tenant.getPackageCode())
                ? sysTenantPackageCapabilityMapper.selectList(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                        .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                        .eq(SysTenantPackageCapabilityEntity::getPackageCode, tenant.getPackageCode()))
                .stream()
                .map(SysTenantPackageCapabilityEntity::getCapabilityCode)
                .distinct()
                .toList()
                : List.of());
        Map<String, SysTenantCapabilityOverrideEntity> overrideMap = sysTenantCapabilityOverrideMapper.selectList(
                new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                        .eq(SysTenantCapabilityOverrideEntity::getTenantId, tenant.getTenantId())
        ).stream().collect(java.util.stream.Collectors.toMap(
                SysTenantCapabilityOverrideEntity::getCapabilityCode,
                java.util.function.Function.identity(),
                (left, right) -> right,
                java.util.LinkedHashMap::new
        ));

        java.util.LinkedHashSet<String> allCodes = new java.util.LinkedHashSet<>(capabilityMap.keySet());
        allCodes.addAll(packageCapabilityCodes);
        allCodes.addAll(overrideMap.keySet());
        List<CapabilityOverrideItemView> items = new java.util.ArrayList<>();
        for (String code : allCodes) {
            SysTenantCapabilityEntity capability = capabilityMap.get(code);
            SysTenantCapabilityOverrideEntity override = overrideMap.get(code);
            boolean packageEnabled = packageCapabilityCodes.contains(code);
            Boolean overrideEnabled = override == null ? null : override.getEnabled() != null && override.getEnabled() == 1;
            boolean effectiveEnabled = overrideEnabled == null ? packageEnabled : overrideEnabled;
            String baseDesc = capabilityDescription(capability);
            String effectiveDesc = override != null && StringUtils.hasText(override.getCapabilityDescOverride())
                    ? override.getCapabilityDescOverride()
                    : baseDesc;
            items.add(new CapabilityOverrideItemView(
                    code,
                    capability == null ? code : capability.getCapabilityName(),
                    baseDesc,
                    packageEnabled,
                    overrideEnabled,
                    effectiveEnabled,
                    override == null ? null : override.getCapabilityDescOverride(),
                    effectiveDesc
            ));
        }
        return new TenantCapabilityOverrideView(
                tenant.getTenantId(),
                tenant.getPackageCode(),
                profile.packageName(),
                packageCapabilityCodes,
                profile.capabilityCodes(),
                items
        );
    }

    private void upsertPackageDefinition(
            String packageCode,
            String packageName,
            Integer userQuota,
            Integer storageQuotaGb,
            List<String> capabilityCodes
    ) {
        SysTenantPackageEntity entity = withPlatformTenant(() -> sysTenantPackageMapper.selectOne(new LambdaQueryWrapper<SysTenantPackageEntity>()
                .eq(SysTenantPackageEntity::getTenantId, "platform")
                .eq(SysTenantPackageEntity::getPackageCode, packageCode)
                .eq(SysTenantPackageEntity::getDeleted, 0)
                .last("limit 1")));
        if (entity == null) {
            entity = new SysTenantPackageEntity();
            entity.setTenantId("platform");
            entity.setPackageCode(packageCode);
            entity.setEnabled(1);
            entity.setPackageDesc(packageName);
            entity.setPackageName(StringUtils.hasText(packageName) ? packageName : packageCode);
            entity.setUserQuota(userQuota);
            entity.setStorageQuotaGb(storageQuotaGb);
            SysTenantPackageEntity insertEntity = entity;
            withPlatformTenant(() -> sysTenantPackageMapper.insert(insertEntity));
        } else {
            entity.setPackageName(StringUtils.hasText(packageName) ? packageName : entity.getPackageName());
            entity.setUserQuota(userQuota);
            entity.setStorageQuotaGb(storageQuotaGb);
            SysTenantPackageEntity updateEntity = entity;
            withPlatformTenant(() -> sysTenantPackageMapper.updateById(updateEntity));
        }
        savePackageCapabilities(packageCode, capabilityCodes);
    }

    private void savePackageCapabilities(String packageCode, List<String> capabilityCodes) {
        if (!StringUtils.hasText(packageCode)) {
            return;
        }
        List<String> normalizedCodes = capabilityCodes == null ? List.of() : capabilityCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        ensureCapabilitiesExist(normalizedCodes);
        withPlatformTenant(() -> sysTenantPackageCapabilityMapper.delete(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                .eq(SysTenantPackageCapabilityEntity::getPackageCode, packageCode)));
        String operator = SecuritySupport.currentOperator();
        for (String code : normalizedCodes) {
            SysTenantPackageCapabilityEntity link = new SysTenantPackageCapabilityEntity();
            link.setTenantId("platform");
            link.setPackageCode(packageCode);
            link.setCapabilityCode(code);
            link.setCreatedBy(operator);
            link.setUpdatedBy(operator);
            withPlatformTenant(() -> sysTenantPackageCapabilityMapper.insert(link));
        }
    }

    private void saveTenantCapabilityOverrides(String tenantId, String packageCode, List<String> requestedCapabilityCodes) {
        List<String> requested = requestedCapabilityCodes == null ? List.of() : requestedCapabilityCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        ensureCapabilitiesExist(requested);
        List<String> packageCapabilities = StringUtils.hasText(packageCode)
                ? sysTenantPackageCapabilityMapper.selectList(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                        .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                        .eq(SysTenantPackageCapabilityEntity::getPackageCode, packageCode))
                .stream().map(SysTenantPackageCapabilityEntity::getCapabilityCode).toList()
                : List.of();
        java.util.Set<String> packageSet = new java.util.LinkedHashSet<>(packageCapabilities);
        java.util.Set<String> requestedSet = new java.util.LinkedHashSet<>(requested);
        sysTenantCapabilityOverrideMapper.delete(new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                .eq(SysTenantCapabilityOverrideEntity::getTenantId, tenantId));
        String operator = SecuritySupport.currentOperator();
        for (String code : requestedSet) {
            if (!packageSet.contains(code)) {
                SysTenantCapabilityOverrideEntity entity = new SysTenantCapabilityOverrideEntity();
                entity.setTenantId(tenantId);
                entity.setCapabilityCode(code);
                entity.setEnabled(1);
                entity.setCreatedBy(operator);
                entity.setUpdatedBy(operator);
                sysTenantCapabilityOverrideMapper.insert(entity);
            }
        }
        for (String code : packageSet) {
            if (!requestedSet.contains(code)) {
                SysTenantCapabilityOverrideEntity entity = new SysTenantCapabilityOverrideEntity();
                entity.setTenantId(tenantId);
                entity.setCapabilityCode(code);
                entity.setEnabled(0);
                entity.setCreatedBy(operator);
                entity.setUpdatedBy(operator);
                sysTenantCapabilityOverrideMapper.insert(entity);
            }
        }
    }

    private void saveTenantCapabilityOverridesFromRequest(
            String tenantId,
            String packageCode,
            List<UpdateTenantCapabilityOverridesRequest.CapabilityOverrideItem> requestedOverrides
    ) {
        List<UpdateTenantCapabilityOverridesRequest.CapabilityOverrideItem> safeOverrides =
                requestedOverrides == null ? List.of() : requestedOverrides;
        List<String> codes = safeOverrides.stream()
                .map(UpdateTenantCapabilityOverridesRequest.CapabilityOverrideItem::capabilityCode)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        ensureCapabilitiesExist(codes);
        java.util.Set<String> packageSet = new java.util.LinkedHashSet<>(withPlatformTenant(() -> StringUtils.hasText(packageCode)
                ? sysTenantPackageCapabilityMapper.selectList(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                        .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                        .eq(SysTenantPackageCapabilityEntity::getPackageCode, packageCode))
                .stream()
                .map(SysTenantPackageCapabilityEntity::getCapabilityCode)
                .toList()
                : List.of()));
        sysTenantCapabilityOverrideMapper.delete(new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                .eq(SysTenantCapabilityOverrideEntity::getTenantId, tenantId));
        String operator = SecuritySupport.currentOperator();
        for (UpdateTenantCapabilityOverridesRequest.CapabilityOverrideItem item : safeOverrides) {
            if (!StringUtils.hasText(item.capabilityCode())) {
                continue;
            }
            String code = item.capabilityCode().trim();
            boolean packageEnabled = packageSet.contains(code);
            Boolean overrideEnabled = item.enabled();
            String descOverride = StringUtils.hasText(item.capabilityDescOverride()) ? item.capabilityDescOverride().trim() : null;
            boolean shouldPersist = overrideEnabled != null || StringUtils.hasText(descOverride);
            if (!shouldPersist) {
                continue;
            }
            SysTenantCapabilityOverrideEntity entity = new SysTenantCapabilityOverrideEntity();
            entity.setTenantId(tenantId);
            entity.setCapabilityCode(code);
            entity.setEnabled(overrideEnabled == null ? (packageEnabled ? 1 : 0) : (overrideEnabled ? 1 : 0));
            entity.setCapabilityDescOverride(descOverride);
            entity.setCreatedBy(operator);
            entity.setUpdatedBy(operator);
            sysTenantCapabilityOverrideMapper.insert(entity);
        }
    }

    private void ensureCapabilitiesExist(List<String> capabilityCodes) {
        if (capabilityCodes == null || capabilityCodes.isEmpty()) {
            return;
        }
        List<String> existing = withPlatformTenant(() -> sysTenantCapabilityMapper.selectList(new LambdaQueryWrapper<SysTenantCapabilityEntity>()
                        .eq(SysTenantCapabilityEntity::getTenantId, "platform")
                        .eq(SysTenantCapabilityEntity::getDeleted, 0)
                        .in(SysTenantCapabilityEntity::getCapabilityCode, capabilityCodes))
                .stream()
                .map(SysTenantCapabilityEntity::getCapabilityCode)
                .toList());
        String operator = SecuritySupport.currentOperator();
        for (String code : capabilityCodes) {
            if (existing.contains(code)) {
                continue;
            }
            SysTenantCapabilityEntity entity = new SysTenantCapabilityEntity();
            entity.setTenantId("platform");
            entity.setCapabilityCode(code);
            entity.setCapabilityName(code);
            entity.setCapabilityDesc(defaultCapabilityDescription(code));
            entity.setSortOrder(999);
            entity.setEnabled(1);
            entity.setCreatedBy(operator);
            entity.setUpdatedBy(operator);
            withPlatformTenant(() -> sysTenantCapabilityMapper.insert(entity));
        }
    }

    private <T> T withPlatformTenant(Supplier<T> supplier) {
        return withTenant("platform", supplier);
    }

    private <T> T withTenant(String tenantId, Supplier<T> supplier) {
        String currentTenantId = TenantContext.getTenantId();
        TenantContext.setTenantId(tenantId);
        try {
            return supplier.get();
        } finally {
            if (StringUtils.hasText(currentTenantId)) {
                TenantContext.setTenantId(currentTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private boolean isPlatformSuperAdmin() {
        return tenantAccessPolicy.isPlatformSuperAdmin();
    }

    private String currentTenantId() {
        return tenantAccessPolicy.currentTenantId();
    }

    private void ensureTenantReadable(String tenantId) {
        tenantAccessPolicy.ensureTenantReadable(tenantId);
    }

    private void requirePlatformSuperAdmin() {
        tenantAccessPolicy.requirePlatformSuperAdmin();
    }

    private String defaultCapabilityDescription(String code) {
        return switch (code) {
            case "auth" -> "轻量登录、会话、强制下线与安全治理能力";
            case "user" -> "用户目录、启停、角色分配与组织可见范围管理";
            case "role" -> "角色模型、权限树与数据范围授权";
            case "dept" -> "组织树、负责人和部门层级治理";
            case "tenant" -> "租户套餐、能力配置与生命周期治理";
            case "system" -> "字典、参数、公告与分类配置管理";
            case "audit" -> "审计查询、导出与授权记录联动";
            case "notice" -> "公告发布与租户通知能力";
            default -> code + " 能力已启用，可在租户侧继续扩展说明。";
        };
    }

    private String capabilityDescription(SysTenantCapabilityEntity capability) {
        if (capability == null || !StringUtils.hasText(capability.getCapabilityDesc())) {
            return "该能力已启用，可在租户侧继续扩展说明。";
        }
        return capability.getCapabilityDesc();
    }

    private String summarizeOverrides(List<CapabilityOverrideItemView> items) {
        return items.stream()
                .filter(item -> item.overrideEnabled() != null || StringUtils.hasText(item.capabilityDescOverride()))
                .map(item -> item.capabilityCode() + ":" + (item.overrideEnabled() == null ? "inherit" : item.overrideEnabled())
                        + ":" + trimToNull(item.capabilityDescOverride()))
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private void recordIfChanged(String tenantId, String changeType, String fieldKey, String oldValue, String newValue, String summary, String operator) {
        tenantChangeLogApplicationService.recordIfChanged(tenantId, changeType, fieldKey, oldValue, newValue, summary, operator);
    }

    private void recordTenantChange(String tenantId, String changeType, String fieldKey, String oldValue, String newValue, String summary, String operator) {
        tenantChangeLogApplicationService.recordTenantChange(tenantId, changeType, fieldKey, oldValue, newValue, summary, operator);
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @Schema(description = "租户变更记录")
    public record TenantChangeView(
            @Schema(description = "记录 ID") Long id,
            @Schema(description = "租户编码") String tenantId,
            @Schema(description = "变更类型") String changeType,
            @Schema(description = "字段键") String fieldKey,
            @Schema(description = "旧值") String oldValue,
            @Schema(description = "新值") String newValue,
            @Schema(description = "变更摘要") String summary,
            @Schema(description = "影响说明") String impactSummary,
            @Schema(description = "操作人") String operator,
            @Schema(description = "变更时间") Long occurredAt
    ) {
    }

    @Schema(description = "租户变更历史摘要")
    public record TenantHistorySummaryView(
            @Schema(description = "租户编码") String tenantId,
            @Schema(description = "命中的变更总数") Integer totalChanges,
            @Schema(description = "套餐变更数") Long packageChanges,
            @Schema(description = "能力变更数") Long capabilityChanges,
            @Schema(description = "状态变更数") Long statusChanges,
            @Schema(description = "资料变更数") Long profileChanges,
            @Schema(description = "字段影响分布") Map<String, Long> affectedFieldCounts,
            @Schema(description = "最近轨迹") List<TenantChangeView> recentTimeline
    ) {
    }

    @Schema(description = "租户能力覆盖视图")
    public record TenantCapabilityOverrideView(
            @Schema(description = "租户编码") String tenantId,
            @Schema(description = "套餐编码") String packageCode,
            @Schema(description = "套餐名称") String packageName,
            @Schema(description = "套餐默认能力编码集合") List<String> packageCapabilityCodes,
            @Schema(description = "当前生效能力编码集合") List<String> effectiveCapabilityCodes,
            @Schema(description = "能力覆盖项") List<CapabilityOverrideItemView> overrides
    ) {
    }

    @Schema(description = "能力覆盖项视图")
    public record CapabilityOverrideItemView(
            @Schema(description = "能力编码") String capabilityCode,
            @Schema(description = "能力名称") String capabilityName,
            @Schema(description = "基础说明") String capabilityDesc,
            @Schema(description = "套餐是否默认启用") boolean packageEnabled,
            @Schema(description = "覆盖启用状态；为空表示继承套餐默认值") Boolean overrideEnabled,
            @Schema(description = "当前是否生效") boolean effectiveEnabled,
            @Schema(description = "说明覆盖") String capabilityDescOverride,
            @Schema(description = "当前展示说明") String effectiveDesc
    ) {
    }

    private record TenantProfile(
            String packageCode,
            String packageName,
            Integer userQuota,
            Integer storageQuotaGb,
            List<String> capabilityCodes,
            Map<String, String> capabilityDescriptions,
            String lifecycleNote
    ) {
        static TenantProfile empty() {
            return new TenantProfile(null, null, null, null, List.of(), Map.of(), null);
        }
    }
}
