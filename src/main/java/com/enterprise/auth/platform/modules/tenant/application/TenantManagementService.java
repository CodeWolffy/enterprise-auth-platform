package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.dept.application.DeptTenantDataFacade;
import com.enterprise.auth.platform.modules.menu.application.MenuService;
import com.enterprise.auth.platform.modules.menu.infrastructure.entity.SysMenuEntity;
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
    private final MenuService menuService;
    private final CatalogService catalogService;
    private final AuditService auditService;
    private final AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService;
    private final TenantAccessPolicy tenantAccessPolicy;
    private final TenantChangeLogApplicationService tenantChangeLogApplicationService;
    private final SecurityPolicyApplicationService securityPolicyApplicationService;
    private final TenantBootstrapFacade tenantBootstrapFacade;
    private final TenantCapabilityResourceScopeFacade tenantCapabilityResourceScopeFacade;

    public TenantManagementService(
            SysTenantMapper sysTenantMapper,
            UserTenantDataFacade userTenantDataFacade,
            RoleTenantDataFacade roleTenantDataFacade,
            DeptTenantDataFacade deptTenantDataFacade,
            SysTenantPackageMapper sysTenantPackageMapper,
            SysTenantCapabilityMapper sysTenantCapabilityMapper,
            SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper,
            SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper,
            MenuService menuService,
            CatalogService catalogService,
            AuditService auditService,
            AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService,
            TenantAccessPolicy tenantAccessPolicy,
            TenantChangeLogApplicationService tenantChangeLogApplicationService,
            SecurityPolicyApplicationService securityPolicyApplicationService,
            TenantBootstrapFacade tenantBootstrapFacade,
            TenantCapabilityResourceScopeFacade tenantCapabilityResourceScopeFacade
    ) {
        this.sysTenantMapper = sysTenantMapper;
        this.userTenantDataFacade = userTenantDataFacade;
        this.roleTenantDataFacade = roleTenantDataFacade;
        this.deptTenantDataFacade = deptTenantDataFacade;
        this.sysTenantPackageMapper = sysTenantPackageMapper;
        this.sysTenantCapabilityMapper = sysTenantCapabilityMapper;
        this.sysTenantPackageCapabilityMapper = sysTenantPackageCapabilityMapper;
        this.sysTenantCapabilityOverrideMapper = sysTenantCapabilityOverrideMapper;
        this.menuService = menuService;
        this.catalogService = catalogService;
        this.auditService = auditService;
        this.permissionSnapshotInvalidationService = permissionSnapshotInvalidationService;
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.tenantChangeLogApplicationService = tenantChangeLogApplicationService;
        this.securityPolicyApplicationService = securityPolicyApplicationService;
        this.tenantBootstrapFacade = tenantBootstrapFacade;
        this.tenantCapabilityResourceScopeFacade = tenantCapabilityResourceScopeFacade;
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
        entity.setAuthBeginAt(TimeSupport.localDateTimeFromEpochMilli(request.authBeginAt()));
        entity.setExpireAt(TimeSupport.localDateTimeFromEpochMilli(request.expireAt()));
        entity.setPackageCode(request.packageCode());
        entity.setLogoUrl(trimToNull(request.logoUrl()));
        entity.setContactName(trimToNull(request.contactName()));
        entity.setContactPhone(trimToNull(request.contactPhone()));
        entity.setContactEmail(trimToNull(request.contactEmail()));
        entity.setWebsite(trimToNull(request.website()));
        entity.setAddress(trimToNull(request.address()));
        entity.setLifecycleNote(request.lifecycleNote());
        sysTenantMapper.insert(entity);
        securityPolicyApplicationService.ensureTenantPolicy(request.tenantId());
        saveTenantProfile(request.tenantId(), request.packageCode(), request.capabilityCodes());
        TenantBootstrapFacade.BootstrapResult bootstrapResult = tenantBootstrapFacade.bootstrap(
                request.tenantId(), request.tenantName(), operator);
        recordTenantChange(request.tenantId(), "CREATED", "tenant", null, request.tenantName(), "创建租户", operator);
        recordTenantChange(request.tenantId(), "CREATED", "bootstrap", null, bootstrapResult.adminUsername(),
                "初始化根部门、管理员角色、管理员用户和默认授权", operator);
        recordTenantChange(request.tenantId(), "STATUS", "tenantStatus", null,
                String.valueOf(entity.getTenantStatus()), "初始化租户状态", operator);
        recordTenantChange(request.tenantId(), "PACKAGE", "packageCode", null,
                request.packageCode(), "初始化租户套餐", operator);
        recordTenantChange(request.tenantId(), "PROFILE", "authBeginAt", null, toStringValue(entity.getAuthBeginAt()), "初始化授权开始时间", operator);
        recordTenantChange(request.tenantId(), "PROFILE", "expireAt", null, toStringValue(entity.getExpireAt()), "初始化授权结束时间", operator);
        recordTenantChange(request.tenantId(), "PROFILE", "contactName", null, request.contactName(), "初始化联系人", operator);
        recordTenantChange(request.tenantId(), "PROFILE", "lifecycleNote", null, request.lifecycleNote(), "初始化运营备注", operator);

        auditService.record("TENANT_CREATED", operator, request.tenantId(), Map.of("tenantId", request.tenantId()));
        evictPrincipalSnapshots();
        return catalogService.tenant(request.tenantId());
    }

    @Transactional
    public CatalogService.TenantView update(String tenantId, CreateTenantRequest request) {
        requirePlatformSuperAdmin();
        SysTenantEntity entity = getTenant(tenantId);
        String oldTenantName = entity.getTenantName();
        Integer oldTenantStatus = entity.getTenantStatus();
        java.time.LocalDateTime oldAuthBeginAt = entity.getAuthBeginAt();
        java.time.LocalDateTime oldExpireAt = entity.getExpireAt();
        String oldLogoUrl = entity.getLogoUrl();
        String oldContactName = entity.getContactName();
        String oldContactPhone = entity.getContactPhone();
        String oldContactEmail = entity.getContactEmail();
        String oldWebsite = entity.getWebsite();
        String oldAddress = entity.getAddress();
        TenantProfile oldProfile = loadTenantProfiles(List.of(entity)).getOrDefault(tenantId, TenantProfile.empty());
        entity.setTenantName(request.tenantName());
        entity.setPlatformLevel(request.platformLevel() ? 1 : 0);
        if (request.tenantStatus() != null) {
            entity.setTenantStatus(request.tenantStatus());
        }
        entity.setAuthBeginAt(TimeSupport.localDateTimeFromEpochMilli(request.authBeginAt()));
        entity.setExpireAt(TimeSupport.localDateTimeFromEpochMilli(request.expireAt()));
        entity.setPackageCode(request.packageCode());
        entity.setLogoUrl(trimToNull(request.logoUrl()));
        entity.setContactName(trimToNull(request.contactName()));
        entity.setContactPhone(trimToNull(request.contactPhone()));
        entity.setContactEmail(trimToNull(request.contactEmail()));
        entity.setWebsite(trimToNull(request.website()));
        entity.setAddress(trimToNull(request.address()));
        entity.setLifecycleNote(request.lifecycleNote());
        sysTenantMapper.updateById(entity);
        saveTenantProfile(tenantId, request.packageCode(), request.capabilityCodes());
        TenantProfile newProfile = loadTenantProfiles(List.of(entity)).getOrDefault(tenantId, TenantProfile.empty());

        String operator = SecuritySupport.currentOperator();
        recordIfChanged(tenantId, "PROFILE", "tenantName", oldTenantName, request.tenantName(), "更新租户名称", operator);
        recordIfChanged(tenantId, "STATUS", "tenantStatus", toStringValue(oldTenantStatus), toStringValue(entity.getTenantStatus()), "更新租户状态", operator);
        recordIfChanged(tenantId, "PROFILE", "authBeginAt", toStringValue(oldAuthBeginAt), toStringValue(entity.getAuthBeginAt()), "更新授权开始时间", operator);
        recordIfChanged(tenantId, "PROFILE", "expireAt", toStringValue(oldExpireAt), toStringValue(entity.getExpireAt()), "更新授权结束时间", operator);
        recordIfChanged(tenantId, "PROFILE", "logoUrl", oldLogoUrl, entity.getLogoUrl(), "更新租户 Logo", operator);
        recordIfChanged(tenantId, "PROFILE", "contactName", oldContactName, entity.getContactName(), "更新联系人姓名", operator);
        recordIfChanged(tenantId, "PROFILE", "contactPhone", oldContactPhone, entity.getContactPhone(), "更新联系人电话", operator);
        recordIfChanged(tenantId, "PROFILE", "contactEmail", oldContactEmail, entity.getContactEmail(), "更新联系人邮箱", operator);
        recordIfChanged(tenantId, "PROFILE", "website", oldWebsite, entity.getWebsite(), "更新官网地址", operator);
        recordIfChanged(tenantId, "PROFILE", "address", oldAddress, entity.getAddress(), "更新联系地址", operator);
        recordIfChanged(tenantId, "PACKAGE", "packageCode", oldProfile.packageCode(), newProfile.packageCode(), "更新租户套餐编码", operator);
        recordIfChanged(tenantId, "PACKAGE", "packageName", oldProfile.packageName(), newProfile.packageName(), "更新租户套餐名称", operator);
        recordIfChanged(tenantId, "PACKAGE", "userQuota", toStringValue(oldProfile.userQuota()), toStringValue(newProfile.userQuota()), "更新用户配额", operator);
        recordIfChanged(tenantId, "PACKAGE", "storageQuotaGb", toStringValue(oldProfile.storageQuotaGb()), toStringValue(newProfile.storageQuotaGb()), "更新存储配额", operator);
        recordIfChanged(tenantId, "CAPABILITY", "capabilityCodes", oldProfile.capabilityCodes().isEmpty() ? null : String.join(",", oldProfile.capabilityCodes()),
                newProfile.capabilityCodes().isEmpty() ? null : String.join(",", newProfile.capabilityCodes()), "更新租户能力范围", operator);
        recordIfChanged(tenantId, "PROFILE", "lifecycleNote", oldProfile.lifecycleNote(), request.lifecycleNote(), "更新运营备注", operator);

        auditService.record("TENANT_UPDATED", operator, tenantId, Map.of("tenantId", tenantId));
        evictPrincipalSnapshots();
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
        withTenant(tenantId, () -> {
            sysTenantCapabilityOverrideMapper.delete(new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                    .eq(SysTenantCapabilityOverrideEntity::getTenantId, tenantId));
            return null;
        });
        recordTenantChange(tenantId, "DELETED", "tenant", entity.getTenantName(), null, "删除租户", operator);
        auditService.record("TENANT_DELETED", operator, tenantId, Map.of("tenantId", entity.getTenantId()));
        evictPrincipalSnapshots();
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

    public TenantCapabilitySummaryView capabilitySummary(String tenantId) {
        ensureTenantReadable(tenantId);
        SysTenantEntity tenant = getTenant(tenantId);
        Map<String, TenantProfile> profiles = loadTenantProfiles(List.of(tenant));
        TenantProfile profile = profiles.getOrDefault(tenantId, TenantProfile.empty());
        TenantCapabilityOverrideView overrideView = buildCapabilityOverrideView(tenant, profile);
        List<String> addedCapabilities = overrideView.overrides().stream()
                .filter(item -> !item.packageEnabled() && item.effectiveEnabled())
                .map(CapabilityOverrideItemView::capabilityCode)
                .toList();
        List<String> disabledCapabilities = overrideView.overrides().stream()
                .filter(item -> item.packageEnabled() && !item.effectiveEnabled())
                .map(CapabilityOverrideItemView::capabilityCode)
                .toList();
        ResourceScopeSummary resourceScopeSummary = buildResourceScopeSummary(tenantId);
        return new TenantCapabilitySummaryView(
                tenant.getTenantId(),
                tenant.getPackageCode(),
                profile.packageName(),
                overrideView.packageCapabilityCodes(),
                overrideView.effectiveCapabilityCodes(),
                addedCapabilities,
                disabledCapabilities,
                overrideView.packageCapabilityCodes().size(),
                overrideView.effectiveCapabilityCodes().size(),
                addedCapabilities.size(),
                disabledCapabilities.size(),
                resourceScopeSummary.visibleMenus(),
                resourceScopeSummary.grantableMenus(),
                resourceScopeSummary.visibleMenuCount(),
                resourceScopeSummary.grantableMenuCount()
        );
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
        evictPrincipalSnapshots();
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
            java.util.List<String> capabilityCodes
    ) {
        ensurePackageExists(packageCode);
        saveTenantCapabilityOverrides(tenantId, packageCode, capabilityCodes);
    }

    private void ensurePackageExists(String packageCode) {
        if (!StringUtils.hasText(packageCode)) {
            return;
        }
        Long count = withPlatformTenant(() -> sysTenantPackageMapper.selectCount(new LambdaQueryWrapper<SysTenantPackageEntity>()
                .eq(SysTenantPackageEntity::getTenantId, "platform")
                .eq(SysTenantPackageEntity::getPackageCode, packageCode)
                .eq(SysTenantPackageEntity::getDeleted, 0)));
        if (count == null || count == 0) {
            throw new BusinessException("租户套餐不存在: " + packageCode);
        }
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
        Map<String, List<SysTenantCapabilityOverrideEntity>> overrides = loadTenantCapabilityOverrides(
                tenants.stream().map(SysTenantEntity::getTenantId).toList()
        );
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
                TimeSupport.toEpochMilli(tenant.getAuthBeginAt()),
                TimeSupport.toEpochMilli(tenant.getExpireAt()),
                profile.packageCode(),
                profile.packageName(),
                profile.userQuota(),
                profile.storageQuotaGb(),
                profile.capabilityCodes(),
                profile.capabilityDescriptions(),
                tenant.getLogoUrl(),
                tenant.getContactName(),
                tenant.getContactPhone(),
                tenant.getContactEmail(),
                tenant.getWebsite(),
                tenant.getAddress(),
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
        Map<String, SysTenantCapabilityOverrideEntity> overrideMap = loadTenantCapabilityOverrides(List.of(tenant.getTenantId()))
                .getOrDefault(tenant.getTenantId(), List.of())
                .stream().collect(java.util.stream.Collectors.toMap(
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

    private void saveTenantCapabilityOverrides(String tenantId, String packageCode, List<String> requestedCapabilityCodes) {
        if (requestedCapabilityCodes == null) {
            clearTenantCapabilityOverrides(tenantId);
            return;
        }
        List<String> requested = requestedCapabilityCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        ensureCapabilitiesExist(requested);
        List<String> packageCapabilities = withPlatformTenant(() -> StringUtils.hasText(packageCode)
                ? sysTenantPackageCapabilityMapper.selectList(new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                        .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                        .eq(SysTenantPackageCapabilityEntity::getPackageCode, packageCode))
                .stream().map(SysTenantPackageCapabilityEntity::getCapabilityCode).toList()
                : List.of());
        java.util.Set<String> packageSet = new java.util.LinkedHashSet<>(packageCapabilities);
        java.util.Set<String> requestedSet = new java.util.LinkedHashSet<>(requested);
        clearTenantCapabilityOverrides(tenantId);
        String operator = SecuritySupport.currentOperator();
        withTenant(tenantId, () -> {
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
            return null;
        });
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
        clearTenantCapabilityOverrides(tenantId);
        String operator = SecuritySupport.currentOperator();
        withTenant(tenantId, () -> {
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
            return null;
        });
    }

    private void clearTenantCapabilityOverrides(String tenantId) {
        withTenant(tenantId, () -> {
            sysTenantCapabilityOverrideMapper.delete(new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                    .eq(SysTenantCapabilityOverrideEntity::getTenantId, tenantId));
            return null;
        });
    }

    private Map<String, List<SysTenantCapabilityOverrideEntity>> loadTenantCapabilityOverrides(List<String> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<SysTenantCapabilityOverrideEntity>> result = new java.util.LinkedHashMap<>();
        for (String tenantId : tenantIds.stream().filter(StringUtils::hasText).distinct().toList()) {
            List<SysTenantCapabilityOverrideEntity> records = withTenant(tenantId, () -> sysTenantCapabilityOverrideMapper.selectList(
                    new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                            .eq(SysTenantCapabilityOverrideEntity::getTenantId, tenantId)
            ));
            result.put(tenantId, records);
        }
        return result;
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
        for (String code : capabilityCodes) {
            if (!existing.contains(code)) {
                throw new BusinessException("能力编码不存在: " + code);
            }
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

    private void evictPrincipalSnapshots() {
        permissionSnapshotInvalidationService.invalidateAll();
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ResourceScopeSummary buildResourceScopeSummary(String tenantId) {
        List<SysMenuEntity> menus = menuService.listTemplateMenus();
        java.util.Set<Long> visibleIds = tenantCapabilityResourceScopeFacade.visibleMenuIds(tenantId, menus);
        java.util.Set<Long> grantableIds = tenantCapabilityResourceScopeFacade.grantableMenuIds(tenantId, menus);
        List<ResourceScopeMenuView> visibleMenus = menus.stream()
                .filter(menu -> menu.getId() != null && visibleIds.contains(menu.getId()))
                .map(ResourceScopeMenuView::from)
                .toList();
        List<ResourceScopeMenuView> grantableMenus = menus.stream()
                .filter(menu -> menu.getId() != null && grantableIds.contains(menu.getId()))
                .map(ResourceScopeMenuView::from)
                .toList();
        return new ResourceScopeSummary(visibleMenus, grantableMenus, visibleMenus.size(), grantableMenus.size());
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

    @Schema(description = "租户能力摘要")
    public record TenantCapabilitySummaryView(
            @Schema(description = "租户编码") String tenantId,
            @Schema(description = "套餐编码") String packageCode,
            @Schema(description = "套餐名称") String packageName,
            @Schema(description = "套餐默认能力编码集合") List<String> packageCapabilityCodes,
            @Schema(description = "当前生效能力编码集合") List<String> effectiveCapabilityCodes,
            @Schema(description = "相对套餐新增的能力编码集合") List<String> addedCapabilities,
            @Schema(description = "相对套餐禁用的能力编码集合") List<String> disabledCapabilities,
            @Schema(description = "套餐默认能力数量") int packageCapabilityCount,
            @Schema(description = "当前生效能力数量") int effectiveCapabilityCount,
            @Schema(description = "新增能力数量") int addedCapabilityCount,
            @Schema(description = "禁用能力数量") int disabledCapabilityCount,
            @Schema(description = "实际可见菜单") List<ResourceScopeMenuView> visibleMenus,
            @Schema(description = "实际可授权菜单") List<ResourceScopeMenuView> grantableMenus,
            @Schema(description = "实际可见菜单数量") int visibleMenuCount,
            @Schema(description = "实际可授权菜单数量") int grantableMenuCount
    ) {
    }

    @Schema(description = "租户资源范围菜单")
    public record ResourceScopeMenuView(
            @Schema(description = "菜单 ID") Long id,
            @Schema(description = "父级菜单 ID") Long parentId,
            @Schema(description = "菜单名称") String name,
            @Schema(description = "菜单类型") String type,
            @Schema(description = "菜单路径") String path,
            @Schema(description = "权限标识") String permission,
            @Schema(description = "路由路径") String routePath
    ) {
        static ResourceScopeMenuView from(SysMenuEntity menu) {
            return new ResourceScopeMenuView(
                    menu.getId(),
                    menu.getParentId(),
                    menu.getName(),
                    menu.getType(),
                    menu.getPath(),
                    menu.getPermission(),
                    menu.getPath()
            );
        }
    }

    private record ResourceScopeSummary(
            List<ResourceScopeMenuView> visibleMenus,
            List<ResourceScopeMenuView> grantableMenus,
            int visibleMenuCount,
            int grantableMenuCount
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
