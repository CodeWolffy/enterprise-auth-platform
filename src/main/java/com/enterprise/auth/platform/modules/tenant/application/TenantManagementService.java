package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.log.application.LogPublisher;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.dept.application.DeptTenantDataFacade;
import com.enterprise.auth.platform.modules.resource.application.CatalogService;
import com.enterprise.auth.platform.modules.role.application.RoleTenantDataFacade;
import com.enterprise.auth.platform.modules.security.application.SecurityPolicyApplicationService;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantPackageEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantPackageMapper;
import com.enterprise.auth.platform.modules.user.application.UserTenantDataFacade;
import com.enterprise.auth.platform.common.authz.SecuritySupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.tenant.interfaces.CreateTenantRequest;
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
    private final TenantMenuService tenantMenuService;
    private final CatalogService catalogService;
    private final LogPublisher logPublisher;
    private final AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService;
    private final TenantAccessPolicy tenantAccessPolicy;
    private final TenantChangeLogApplicationService tenantChangeLogApplicationService;
    private final SecurityPolicyApplicationService securityPolicyApplicationService;
    private final TenantBootstrapFacade tenantBootstrapFacade;

    public TenantManagementService(
            SysTenantMapper sysTenantMapper,
            UserTenantDataFacade userTenantDataFacade,
            RoleTenantDataFacade roleTenantDataFacade,
            DeptTenantDataFacade deptTenantDataFacade,
            SysTenantPackageMapper sysTenantPackageMapper,
            TenantMenuService tenantMenuService,
            CatalogService catalogService,
            LogPublisher logPublisher,
            AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService,
            TenantAccessPolicy tenantAccessPolicy,
            TenantChangeLogApplicationService tenantChangeLogApplicationService,
            SecurityPolicyApplicationService securityPolicyApplicationService,
            TenantBootstrapFacade tenantBootstrapFacade
    ) {
        this.sysTenantMapper = sysTenantMapper;
        this.userTenantDataFacade = userTenantDataFacade;
        this.roleTenantDataFacade = roleTenantDataFacade;
        this.deptTenantDataFacade = deptTenantDataFacade;
        this.sysTenantPackageMapper = sysTenantPackageMapper;
        this.tenantMenuService = tenantMenuService;
        this.catalogService = catalogService;
        this.logPublisher = logPublisher;
        this.permissionSnapshotInvalidationService = permissionSnapshotInvalidationService;
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.tenantChangeLogApplicationService = tenantChangeLogApplicationService;
        this.securityPolicyApplicationService = securityPolicyApplicationService;
        this.tenantBootstrapFacade = tenantBootstrapFacade;
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
        ensurePackageExists(request.packageCode());
        tenantMenuService.saveTenantMenuByPackage(request.tenantId(), request.packageCode());
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
        ensurePackageExists(request.packageCode());
        if (!java.util.Objects.equals(oldProfile.packageCode(), request.packageCode())) {
            tenantMenuService.saveTenantMenuByPackage(tenantId, request.packageCode());
        }
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
        recordIfChanged(tenantId, "PROFILE", "lifecycleNote", oldProfile.lifecycleNote(), request.lifecycleNote(), "更新运营备注", operator);

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
        recordTenantChange(tenantId, "DELETED", "tenant", entity.getTenantName(), null, "删除租户", operator);
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
        Map<String, TenantProfile> result = new java.util.LinkedHashMap<>();
        for (SysTenantEntity tenant : tenants) {
            SysTenantPackageEntity pkg = packages.get(tenant.getPackageCode());
            result.put(tenant.getTenantId(), new TenantProfile(
                    tenant.getPackageCode(),
                    pkg == null ? null : pkg.getPackageName(),
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
                tenant.getLogoUrl(),
                tenant.getContactName(),
                tenant.getContactPhone(),
                tenant.getContactEmail(),
                tenant.getWebsite(),
                tenant.getAddress(),
                profile.lifecycleNote()
        );
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
            @Schema(description = "菜单授权变更数") Long menuChanges,
            @Schema(description = "状态变更数") Long statusChanges,
            @Schema(description = "资料变更数") Long profileChanges,
            @Schema(description = "字段影响分布") Map<String, Long> affectedFieldCounts,
            @Schema(description = "最近轨迹") List<TenantChangeView> recentTimeline
    ) {
    }


    private record TenantProfile(
            String packageCode,
            String packageName,
            String lifecycleNote
    ) {
        static TenantProfile empty() {
            return new TenantProfile(null, null, null);
        }
    }
}
