package com.enterprise.auth.platform.modules.resource.application;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.dept.application.DeptCatalogFacade;
import com.enterprise.auth.platform.modules.role.application.RoleCatalogFacade;
import com.enterprise.auth.platform.modules.role.application.RolePayloadCodec;
import com.enterprise.auth.platform.modules.tenant.application.TenantProfileFacade;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.context.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final RoleCatalogFacade roleCatalogFacade;
    private final DeptCatalogFacade deptCatalogFacade;
    private final TenantProfileFacade tenantProfileFacade;
    private final DataScopeService dataScopeService;
    private final RolePayloadCodec rolePayloadCodec;

    public CatalogService(
            RoleCatalogFacade roleCatalogFacade,
            DeptCatalogFacade deptCatalogFacade,
            TenantProfileFacade tenantProfileFacade,
            DataScopeService dataScopeService,
            RolePayloadCodec rolePayloadCodec
    ) {
        this.roleCatalogFacade = roleCatalogFacade;
        this.deptCatalogFacade = deptCatalogFacade;
        this.tenantProfileFacade = tenantProfileFacade;
        this.dataScopeService = dataScopeService;
        this.rolePayloadCodec = rolePayloadCodec;
    }

    public List<RoleView> roles() {
        String tenantId = currentTenantId();
        return roleCatalogFacade.listRoleItems(tenantId)
                .stream()
                .map(role -> new RoleView(
                        role.id(),
                        role.roleCode(),
                        role.roleName(),
                        role.roleDesc(),
                        parseScope(role.dataScopeType()),
                        rolePayloadCodec.readDeptIds(role.dataScopeValueJson()).stream().sorted().toList()
                ))
                .toList();
    }
    public List<DepartmentView> departments() {
        String tenantId = currentTenantId();
        List<DeptCatalogFacade.DeptItem> departments = deptCatalogFacade.listDeptItems(tenantId);
        Set<Long> visibleDeptIds = dataScopeService.visibleDeptIds(tenantId).orElse(null);
        if (visibleDeptIds != null) {
            departments = departments.stream()
                    .filter(d -> d.id() != null && visibleDeptIds.contains(d.id()))
                    .toList();
        }
        return departments.stream()
                .map(dept -> new DepartmentView(
                        dept.id(),
                        dept.deptCode(),
                        dept.deptName(),
                        dept.parentId(),
                        dept.leaderUserId(),
                        dept.leaderName(),
                        dept.leaderPhone(),
                        dept.orderNo(),
                        dept.enabled()
                ))
                .toList();
    }
    public List<TenantView> tenants() {
        List<TenantProfileFacade.TenantRecord> tenants = tenantProfileFacade.listTenantRecords();
        Map<String, TenantProfile> profiles = loadTenantProfiles(tenants);
        return tenants.stream()
                .map(tenant -> {
                    TenantProfile profile = profiles.getOrDefault(tenant.tenantId(), TenantProfile.empty());
                    return new TenantView(
                            tenant.tenantId(),
                            tenant.tenantName(),
                            tenant.platformLevel() != null && tenant.platformLevel() == 1,
                            tenant.tenantStatus(),
                            TimeSupport.toEpochMilli(tenant.authBeginAt()),
                            TimeSupport.toEpochMilli(tenant.expireAt()),
                            profile.packageCode(),
                            profile.packageName(),
                            profile.userQuota(),
                            profile.storageQuotaGb(),
                            profile.capabilityCodes(),
                            profile.capabilityDescriptions(),
                            tenant.logoUrl(),
                            tenant.contactName(),
                            tenant.contactPhone(),
                            tenant.contactEmail(),
                            tenant.website(),
                            tenant.address(),
                            profile.lifecycleNote()
                    );
                })
                .toList();
    }
    public RoleView role(String roleCode) {
        return roles().stream()
                .filter(role -> role.code().equals(roleCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException("角色不存在"));
    }

    public TenantView tenant(String tenantId) {
        return tenants().stream()
                .filter(tenant -> tenant.tenantId().equals(tenantId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("租户不存在"));
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }

    private DataScopeType parseScope(String scopeType) {
        if (!StringUtils.hasText(scopeType)) {
            return DataScopeType.SELF;
        }
        try {
            return DataScopeType.valueOf(scopeType);
        } catch (IllegalArgumentException ex) {
            log.debug("未知的目录数据范围类型，回退为 SELF。value={}", scopeType);
            return DataScopeType.SELF;
        }
    }

    private Map<String, TenantProfile> loadTenantProfiles(List<TenantProfileFacade.TenantRecord> tenants) {
        if (tenants.isEmpty()) {
            return Map.of();
        }
        List<String> tenantIds = tenants.stream().map(TenantProfileFacade.TenantRecord::tenantId).toList();
        List<String> packageCodes = tenants.stream()
                .map(TenantProfileFacade.TenantRecord::packageCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, TenantProfileFacade.PackageRecord> packages = tenantProfileFacade.loadPackageRecords(packageCodes);
        Map<String, TenantProfileFacade.CapabilityRecord> capabilities = tenantProfileFacade.loadCapabilityRecords();
        Map<String, List<String>> packageCapabilities = tenantProfileFacade.loadPackageCapabilities(packageCodes);
        Map<String, List<TenantProfileFacade.OverrideRecord>> overrides = tenantProfileFacade.loadOverrideRecords(tenantIds);
        Map<String, TenantProfile> result = new LinkedHashMap<>();
        for (TenantProfileFacade.TenantRecord tenant : tenants) {
            TenantProfileFacade.PackageRecord pkg = packages.get(tenant.packageCode());
            List<String> capabilityCodes = new java.util.ArrayList<>(packageCapabilities.getOrDefault(tenant.packageCode(), List.of()));
            Map<String, String> descriptions = capabilityCodes.stream().collect(Collectors.toMap(
                    java.util.function.Function.identity(),
                    code -> tenantProfileFacade.capabilityDescription(capabilities.get(code)),
                    (left, right) -> right,
                    LinkedHashMap::new
            ));
            for (TenantProfileFacade.OverrideRecord override : overrides.getOrDefault(tenant.tenantId(), List.of())) {
                if (override.enabled() != null && override.enabled() == 1) {
                    if (!capabilityCodes.contains(override.capabilityCode())) {
                        capabilityCodes.add(override.capabilityCode());
                    }
                    descriptions.put(override.capabilityCode(), StringUtils.hasText(override.capabilityDescOverride())
                            ? override.capabilityDescOverride()
                            : tenantProfileFacade.capabilityDescription(capabilities.get(override.capabilityCode())));
                } else {
                    capabilityCodes.remove(override.capabilityCode());
                    descriptions.remove(override.capabilityCode());
                }
            }
            result.put(tenant.tenantId(), new TenantProfile(
                    tenant.packageCode(),
                    pkg == null ? null : pkg.packageName(),
                    pkg == null ? null : pkg.userQuota(),
                    pkg == null ? null : pkg.storageQuotaGb(),
                    capabilityCodes,
                    descriptions,
                    tenant.lifecycleNote()
            ));
        }
        return result;
    }

    @Schema(description = "角色目录项")
    public record RoleView(
            @Schema(description = "角色 ID") Long id,
            @Schema(description = "角色编码") String code,
            @Schema(description = "角色名称") String name,
            @Schema(description = "角色描述") String description,
            @Schema(description = "数据权限范围") DataScopeType dataScopeType,
            @Schema(description = "自定义部门 ID 集合") List<Long> customDeptIds
    ) {
    }

    @Schema(description = "部门目录项")
    public record DepartmentView(
            @Schema(description = "部门 ID") Long id,
            @Schema(description = "部门编码") String code,
            @Schema(description = "部门名称") String name,
            @Schema(description = "父部门 ID") Long parentId,
            @Schema(description = "负责人用户 ID") Long leaderUserId,
            @Schema(description = "负责人姓名") String leaderName,
            @Schema(description = "负责人电话") String leaderPhone,
            @Schema(description = "排序序号") Integer orderNo,
            @Schema(description = "启用状态：0 停用，1 启用") Integer enabled
    ) {
    }

    @Schema(description = "租户目录项")
    public record TenantView(
            @Schema(description = "租户编码") String tenantId,
            @Schema(description = "租户名称") String name,
            @Schema(description = "是否平台级租户") boolean platformLevel,
            @Schema(description = "租户状态") Integer tenantStatus,
            @Schema(description = "授权开始时间") Long authBeginAt,
            @Schema(description = "授权结束时间") Long expireAt,
            @Schema(description = "套餐编码") String packageCode,
            @Schema(description = "套餐名称") String packageName,
            @Schema(description = "用户配额") Integer userQuota,
            @Schema(description = "存储配额(GB)") Integer storageQuotaGb,
            @Schema(description = "能力编码集合") List<String> capabilityCodes,
            @Schema(description = "能力说明映射") Map<String, String> capabilityDescriptions,
            @Schema(description = "Logo 地址") String logoUrl,
            @Schema(description = "联系人姓名") String contactName,
            @Schema(description = "联系人电话") String contactPhone,
            @Schema(description = "联系人邮箱") String contactEmail,
            @Schema(description = "官网地址") String website,
            @Schema(description = "联系地址") String address,
            @Schema(description = "运营备注") String lifecycleNote
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
