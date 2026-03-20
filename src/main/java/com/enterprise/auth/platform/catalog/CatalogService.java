package com.enterprise.auth.platform.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.common.model.MenuItem;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysDeptEntity;
import com.enterprise.auth.platform.persistence.entity.SysPermissionEntity;
import com.enterprise.auth.platform.persistence.entity.SysRoleDeptScopeEntity;
import com.enterprise.auth.platform.persistence.entity.SysRoleEntity;
import com.enterprise.auth.platform.persistence.entity.SysTenantCapabilityEntity;
import com.enterprise.auth.platform.persistence.entity.SysTenantCapabilityOverrideEntity;
import com.enterprise.auth.platform.persistence.entity.SysTenantEntity;
import com.enterprise.auth.platform.persistence.entity.SysTenantPackageCapabilityEntity;
import com.enterprise.auth.platform.persistence.entity.SysTenantPackageEntity;
import com.enterprise.auth.platform.persistence.mapper.SysDeptMapper;
import com.enterprise.auth.platform.persistence.mapper.SysPermissionMapper;
import com.enterprise.auth.platform.persistence.mapper.SysRoleDeptScopeMapper;
import com.enterprise.auth.platform.persistence.mapper.SysRoleMapper;
import com.enterprise.auth.platform.persistence.mapper.SysTenantCapabilityMapper;
import com.enterprise.auth.platform.persistence.mapper.SysTenantCapabilityOverrideMapper;
import com.enterprise.auth.platform.persistence.mapper.SysTenantMapper;
import com.enterprise.auth.platform.persistence.mapper.SysTenantPackageCapabilityMapper;
import com.enterprise.auth.platform.persistence.mapper.SysTenantPackageMapper;
import com.enterprise.auth.platform.security.DataScopeService;
import com.enterprise.auth.platform.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CatalogService {

    private static final List<MenuRule> MENU_RULES = List.of(
            new MenuRule(new MenuItem("dashboard", "运行总览", "/dashboard", "DashboardView"), Set.of("auth:read")),
            new MenuRule(new MenuItem("oauth-clients", "OAuth2 客户端", "/oauth-clients", "OAuthClientsView"), Set.of("auth:read")),
            new MenuRule(new MenuItem("users", "用户管理", "/system/users", "UsersView"), Set.of("user:read")),
            new MenuRule(new MenuItem("roles", "角色管理", "/system/roles", "RolesView"), Set.of("role:read")),
            new MenuRule(new MenuItem("permissions", "权限管理", "/system/permissions", "PermissionsView"), Set.of("permission:read")),
            new MenuRule(new MenuItem("depts", "部门管理", "/system/depts", "DepartmentsView"), Set.of("dept:read")),
            new MenuRule(new MenuItem("tenants", "租户管理", "/system/tenants", "TenantsView"), Set.of("tenant:read")),
            new MenuRule(new MenuItem("audit", "安全审计", "/system/audit", "AuditView"), Set.of("audit:read")),
            new MenuRule(new MenuItem("settings", "系统管理", "/system/settings", "SystemManagementView"), Set.of("system:read"))
    );

    private final PersistenceProperties persistenceProperties;
    private final SysRoleMapper sysRoleMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysTenantMapper sysTenantMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysRoleDeptScopeMapper sysRoleDeptScopeMapper;
    private final SysTenantPackageMapper sysTenantPackageMapper;
    private final SysTenantCapabilityMapper sysTenantCapabilityMapper;
    private final SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper;
    private final SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper;
    private final DataScopeService dataScopeService;

    public CatalogService(
            PersistenceProperties persistenceProperties,
            @Nullable SysRoleMapper sysRoleMapper,
            @Nullable SysDeptMapper sysDeptMapper,
            @Nullable SysTenantMapper sysTenantMapper,
            @Nullable SysPermissionMapper sysPermissionMapper,
            @Nullable SysRoleDeptScopeMapper sysRoleDeptScopeMapper,
            @Nullable SysTenantPackageMapper sysTenantPackageMapper,
            @Nullable SysTenantCapabilityMapper sysTenantCapabilityMapper,
            @Nullable SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper,
            @Nullable SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper,
            DataScopeService dataScopeService
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysRoleMapper = sysRoleMapper;
        this.sysDeptMapper = sysDeptMapper;
        this.sysTenantMapper = sysTenantMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysRoleDeptScopeMapper = sysRoleDeptScopeMapper;
        this.sysTenantPackageMapper = sysTenantPackageMapper;
        this.sysTenantCapabilityMapper = sysTenantCapabilityMapper;
        this.sysTenantPackageCapabilityMapper = sysTenantPackageCapabilityMapper;
        this.sysTenantCapabilityOverrideMapper = sysTenantCapabilityOverrideMapper;
        this.dataScopeService = dataScopeService;
    }

    public List<MenuItem> menusFor(Set<String> permissions) {
        return MENU_RULES.stream()
                .filter(rule -> permissions.containsAll(rule.permissions()))
                .map(MenuRule::menu)
                .toList();
    }

    public List<RoleView> roles() {
        String tenantId = currentTenantId();
        if (databaseEnabled() && sysRoleMapper != null) {
            Map<String, List<Long>> customDeptIdsByRoleCode = loadRoleCustomDeptIds(tenantId);
            return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                            .eq(SysRoleEntity::getTenantId, tenantId)
                            .eq(SysRoleEntity::getDeleted, 0)
                            .orderByAsc(SysRoleEntity::getId))
                    .stream()
                    .map(role -> new RoleView(
                            role.getId(),
                            role.getRoleCode(),
                            role.getRoleName(),
                            role.getRoleDesc(),
                            parseScope(role.getDataScopeType()),
                            customDeptIdsByRoleCode.getOrDefault(role.getRoleCode(), List.of())
                    ))
                    .toList();
        }
        if ("platform".equals(tenantId)) {
            return List.of(new RoleView(1L, "ADMIN", "平台管理员", "全局系统管理角色", DataScopeType.ALL, List.of()));
        }
        return List.of(
                new RoleView(2L, "TENANT_ADMIN", "租户管理员", "租户级管理角色", DataScopeType.DEPT_AND_CHILDREN, List.of()),
                new RoleView(3L, "AUDITOR", "审计员", "只读审计与用户查看角色", DataScopeType.DEPT, List.of())
        );
    }

    public List<DepartmentView> departments() {
        String tenantId = currentTenantId();
        if (databaseEnabled() && sysDeptMapper != null) {
            List<SysDeptEntity> departments = sysDeptMapper.selectList(new LambdaQueryWrapper<SysDeptEntity>()
                    .eq(SysDeptEntity::getTenantId, tenantId)
                    .eq(SysDeptEntity::getDeleted, 0)
                    .orderByAsc(SysDeptEntity::getId));
            departments = dataScopeService.filterDepartments(tenantId, departments);
            return departments.stream()
                    .map(dept -> new DepartmentView(
                            dept.getId(),
                            dept.getDeptCode(),
                            dept.getDeptName(),
                            dept.getParentId(),
                            dept.getLeaderUserId()
                    ))
                    .toList();
        }
        if ("platform".equals(tenantId)) {
            return List.of(new DepartmentView(1000L, "OPS", "平台运营中心", null, null));
        }
        return List.of(
                new DepartmentView(1001L, "FIN", "租户 A 财务部", null, null),
                new DepartmentView(1002L, "RD", "租户 A 研发部", null, null)
        );
    }

    public List<TenantView> tenants() {
        if (databaseEnabled() && sysTenantMapper != null) {
            List<SysTenantEntity> tenants = sysTenantMapper.selectList(new LambdaQueryWrapper<SysTenantEntity>()
                    .eq(SysTenantEntity::getDeleted, 0)
                    .orderByAsc(SysTenantEntity::getId));
            Map<String, TenantProfile> profiles = loadTenantProfiles(tenants);
            return tenants.stream()
                    .map(tenant -> {
                        TenantProfile profile = profiles.getOrDefault(tenant.getTenantId(), TenantProfile.empty());
                        return new TenantView(
                                tenant.getTenantId(),
                                tenant.getTenantName(),
                                tenant.getPlatformLevel() != null && tenant.getPlatformLevel() == 1,
                                tenant.getTenantStatus(),
                                tenant.getExpireAt(),
                                profile.packageCode(),
                                profile.packageName(),
                                profile.userQuota(),
                                profile.storageQuotaGb(),
                                profile.capabilityCodes(),
                                profile.capabilityDescriptions(),
                                profile.lifecycleNote()
                        );
                    })
                    .toList();
        }
        return List.of(
                new TenantView("platform", "平台租户", true, 1, null, "platform-governance", "平台治理版", 9999, 1024,
                        List.of("oauth", "audit", "system", "tenant"),
                        Map.of("oauth", "统一认证中心与开放授权能力", "audit", "全局审计检索与导出能力", "system", "平台级系统配置与分类管理", "tenant", "租户治理与套餐运维"),
                        "负责全局治理与租户运维"),
                new TenantView("tenant-a", "租户 A", false, 1, null, "business-standard", "标准版", 200, 200,
                        List.of("user", "role", "audit", "notice"),
                        Map.of("user", "用户与身份目录管理", "role", "角色与授权范围管理", "audit", "安全审计检索能力", "notice", "租户公告发布能力"),
                        "默认标准业务租户")
        );
    }

    public List<PermissionView> permissions() {
        String tenantId = currentTenantId();
        if (databaseEnabled() && sysPermissionMapper != null) {
            return sysPermissionMapper.selectList(new LambdaQueryWrapper<SysPermissionEntity>()
                            .eq(SysPermissionEntity::getTenantId, tenantId)
                            .eq(SysPermissionEntity::getDeleted, 0)
                            .orderByAsc(SysPermissionEntity::getId))
                    .stream()
                    .map(permission -> new PermissionView(
                            permission.getId(),
                            permission.getResourceCode(),
                            permission.getActionCode(),
                            permission.getScopeCode(),
                            permission.getPermissionName(),
                            permission.getPermissionCode()
                    ))
                    .toList();
        }
        if ("platform".equals(tenantId)) {
            return List.of(
                    new PermissionView(1L, "auth", "read", "tenant", "认证读取", "auth:read"),
                    new PermissionView(2L, "auth", "write", "tenant", "认证写入", "auth:write"),
                    new PermissionView(3L, "user", "read", "tenant", "用户读取", "user:read"),
                    new PermissionView(4L, "user", "write", "tenant", "用户写入", "user:write"),
                    new PermissionView(5L, "role", "read", "tenant", "角色读取", "role:read"),
                    new PermissionView(6L, "role", "write", "tenant", "角色写入", "role:write"),
                    new PermissionView(7L, "permission", "read", "tenant", "权限读取", "permission:read"),
                    new PermissionView(8L, "permission", "write", "tenant", "权限写入", "permission:write"),
                    new PermissionView(9L, "dept", "read", "tenant", "部门读取", "dept:read"),
                    new PermissionView(10L, "dept", "write", "tenant", "部门写入", "dept:write"),
                    new PermissionView(11L, "tenant", "read", "platform", "租户读取", "tenant:read"),
                    new PermissionView(12L, "tenant", "write", "platform", "租户写入", "tenant:write"),
                    new PermissionView(13L, "audit", "read", "tenant", "审计读取", "audit:read"),
                    new PermissionView(14L, "audit", "write", "tenant", "审计写入", "audit:write"),
                    new PermissionView(15L, "system", "read", "tenant", "系统读取", "system:read"),
                    new PermissionView(16L, "system", "write", "tenant", "系统写入", "system:write"),
                    new PermissionView(17L, "session", "write", "tenant", "会话写入", "session:write")
            );
        }
        return List.of(
                new PermissionView(18L, "audit", "read", "tenant", "审计读取", "audit:read"),
                new PermissionView(19L, "user", "read", "tenant", "用户读取", "user:read"),
                new PermissionView(20L, "permission", "read", "tenant", "权限读取", "permission:read")
        );
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

    public List<PermissionView> permissionsByCodes(Set<String> permissionCodes) {
        return permissions().stream()
                .filter(permission -> permissionCodes.contains(permission.permissionCode()))
                .toList();
    }

    private boolean databaseEnabled() {
        return persistenceProperties.databaseEnabled();
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }

    private DataScopeType parseScope(String scopeType) {
        try {
            return DataScopeType.valueOf(scopeType);
        } catch (Exception ignored) {
            return DataScopeType.SELF;
        }
    }

    private Map<String, TenantProfile> loadTenantProfiles(List<SysTenantEntity> tenants) {
        if (tenants.isEmpty()
                || sysTenantPackageMapper == null
                || sysTenantCapabilityMapper == null
                || sysTenantPackageCapabilityMapper == null
                || sysTenantCapabilityOverrideMapper == null) {
            return Map.of();
        }
        List<String> tenantIds = tenants.stream().map(SysTenantEntity::getTenantId).toList();
        List<String> packageCodes = tenants.stream()
                .map(SysTenantEntity::getPackageCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, SysTenantPackageEntity> packages = packageCodes.isEmpty() ? Map.of() : sysTenantPackageMapper.selectList(
                new LambdaQueryWrapper<SysTenantPackageEntity>()
                        .eq(SysTenantPackageEntity::getTenantId, "platform")
                        .eq(SysTenantPackageEntity::getDeleted, 0)
                        .in(SysTenantPackageEntity::getPackageCode, packageCodes)
        ).stream().collect(java.util.stream.Collectors.toMap(
                SysTenantPackageEntity::getPackageCode,
                java.util.function.Function.identity(),
                (left, right) -> right,
                LinkedHashMap::new
        ));
        Map<String, SysTenantCapabilityEntity> capabilities = sysTenantCapabilityMapper.selectList(
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
                LinkedHashMap::new
        ));
        Map<String, List<String>> packageCapabilities = packageCodes.isEmpty() ? Map.of() : sysTenantPackageCapabilityMapper.selectList(
                new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                        .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                        .in(SysTenantPackageCapabilityEntity::getPackageCode, packageCodes)
        ).stream().collect(java.util.stream.Collectors.groupingBy(
                SysTenantPackageCapabilityEntity::getPackageCode,
                LinkedHashMap::new,
                java.util.stream.Collectors.mapping(SysTenantPackageCapabilityEntity::getCapabilityCode, java.util.stream.Collectors.toList())
        ));
        Map<String, List<SysTenantCapabilityOverrideEntity>> overrides = sysTenantCapabilityOverrideMapper.selectList(
                new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                        .in(SysTenantCapabilityOverrideEntity::getTenantId, tenantIds)
        ).stream().collect(java.util.stream.Collectors.groupingBy(
                SysTenantCapabilityOverrideEntity::getTenantId,
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
        ));
        Map<String, TenantProfile> result = new LinkedHashMap<>();
        for (SysTenantEntity tenant : tenants) {
            SysTenantPackageEntity pkg = packages.get(tenant.getPackageCode());
            List<String> capabilityCodes = new java.util.ArrayList<>(packageCapabilities.getOrDefault(tenant.getPackageCode(), List.of()));
            Map<String, String> descriptions = capabilityCodes.stream().collect(java.util.stream.Collectors.toMap(
                    java.util.function.Function.identity(),
                    code -> capabilityDescription(capabilities.get(code)),
                    (left, right) -> right,
                    LinkedHashMap::new
            ));
            for (SysTenantCapabilityOverrideEntity override : overrides.getOrDefault(tenant.getTenantId(), List.of())) {
                if (override.getEnabled() != null && override.getEnabled() == 1) {
                    if (!capabilityCodes.contains(override.getCapabilityCode())) {
                        capabilityCodes.add(override.getCapabilityCode());
                    }
                    descriptions.put(override.getCapabilityCode(), StringUtils.hasText(override.getCapabilityDescOverride())
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

    private Map<String, List<Long>> loadRoleCustomDeptIds(String tenantId) {
        if (sysRoleDeptScopeMapper == null) {
            return Map.of();
        }
        Map<Long, String> roleCodeById = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                        .eq(SysRoleEntity::getTenantId, tenantId)
                        .eq(SysRoleEntity::getDeleted, 0))
                .stream()
                .collect(java.util.stream.Collectors.toMap(SysRoleEntity::getId, SysRoleEntity::getRoleCode));
        return sysRoleDeptScopeMapper.selectList(new LambdaQueryWrapper<SysRoleDeptScopeEntity>()
                        .eq(SysRoleDeptScopeEntity::getTenantId, tenantId))
                .stream()
                .filter(item -> roleCodeById.containsKey(item.getRoleId()))
                .collect(java.util.stream.Collectors.groupingBy(
                        item -> roleCodeById.get(item.getRoleId()),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(SysRoleDeptScopeEntity::getDeptId, java.util.stream.Collectors.toList())
                ));
    }

    private String capabilityDescription(SysTenantCapabilityEntity capability) {
        if (capability == null || !StringUtils.hasText(capability.getCapabilityDesc())) {
            return "该能力已启用，可在租户侧使用对应模块。";
        }
        return capability.getCapabilityDesc();
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
            @Schema(description = "负责人用户 ID") Long leaderUserId
    ) {
    }

    @Schema(description = "租户目录项")
    public record TenantView(
            @Schema(description = "租户编码") String tenantId,
            @Schema(description = "租户名称") String name,
            @Schema(description = "是否平台级租户") boolean platformLevel,
            @Schema(description = "租户状态") Integer tenantStatus,
            @Schema(description = "到期时间") LocalDateTime expireAt,
            @Schema(description = "套餐编码") String packageCode,
            @Schema(description = "套餐名称") String packageName,
            @Schema(description = "用户配额") Integer userQuota,
            @Schema(description = "存储配额(GB)") Integer storageQuotaGb,
            @Schema(description = "能力编码集合") List<String> capabilityCodes,
            @Schema(description = "能力说明映射") Map<String, String> capabilityDescriptions,
            @Schema(description = "运营备注") String lifecycleNote
    ) {
    }

    @Schema(description = "权限目录项")
    public record PermissionView(
            @Schema(description = "权限 ID") Long id,
            @Schema(description = "资源编码") String resourceCode,
            @Schema(description = "动作编码") String actionCode,
            @Schema(description = "作用域编码") String scopeCode,
            @Schema(description = "权限名称") String permissionName,
            @Schema(description = "权限编码") String permissionCode
    ) {
    }

    private record MenuRule(MenuItem menu, Set<String> permissions) {
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
