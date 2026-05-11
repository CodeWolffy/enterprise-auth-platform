package com.enterprise.auth.platform.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.common.time.TimeSupport;
import com.enterprise.auth.platform.model.entity.SysDeptEntity;
import com.enterprise.auth.platform.model.entity.SysRoleEntity;
import com.enterprise.auth.platform.model.entity.SysTenantCapabilityEntity;
import com.enterprise.auth.platform.model.entity.SysTenantCapabilityOverrideEntity;
import com.enterprise.auth.platform.model.entity.SysTenantEntity;
import com.enterprise.auth.platform.model.entity.SysTenantPackageCapabilityEntity;
import com.enterprise.auth.platform.model.entity.SysTenantPackageEntity;
import com.enterprise.auth.platform.model.mapper.SysDeptMapper;
import com.enterprise.auth.platform.model.mapper.SysRoleMapper;
import com.enterprise.auth.platform.model.mapper.SysTenantCapabilityMapper;
import com.enterprise.auth.platform.model.mapper.SysTenantCapabilityOverrideMapper;
import com.enterprise.auth.platform.model.mapper.SysTenantMapper;
import com.enterprise.auth.platform.model.mapper.SysTenantPackageCapabilityMapper;
import com.enterprise.auth.platform.model.mapper.SysTenantPackageMapper;
import com.enterprise.auth.platform.role.support.RolePayloadCodec;
import com.enterprise.auth.platform.security.DataScopeService;
import com.enterprise.auth.platform.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CatalogService {

    private final SysRoleMapper sysRoleMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysTenantMapper sysTenantMapper;
    private final SysTenantPackageMapper sysTenantPackageMapper;
    private final SysTenantCapabilityMapper sysTenantCapabilityMapper;
    private final SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper;
    private final SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper;
    private final DataScopeService dataScopeService;
    private final RolePayloadCodec rolePayloadCodec;

    public CatalogService(
            SysRoleMapper sysRoleMapper,
            SysDeptMapper sysDeptMapper,
            SysTenantMapper sysTenantMapper,
            SysTenantPackageMapper sysTenantPackageMapper,
            SysTenantCapabilityMapper sysTenantCapabilityMapper,
            SysTenantPackageCapabilityMapper sysTenantPackageCapabilityMapper,
            SysTenantCapabilityOverrideMapper sysTenantCapabilityOverrideMapper,
            DataScopeService dataScopeService,
            RolePayloadCodec rolePayloadCodec
    ) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysDeptMapper = sysDeptMapper;
        this.sysTenantMapper = sysTenantMapper;
        this.sysTenantPackageMapper = sysTenantPackageMapper;
        this.sysTenantCapabilityMapper = sysTenantCapabilityMapper;
        this.sysTenantPackageCapabilityMapper = sysTenantPackageCapabilityMapper;
        this.sysTenantCapabilityOverrideMapper = sysTenantCapabilityOverrideMapper;
        this.dataScopeService = dataScopeService;
        this.rolePayloadCodec = rolePayloadCodec;
    }

    public List<RoleView> roles() {
        String tenantId = currentTenantId();
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
                        rolePayloadCodec.readDeptIds(role.getDataScopeValueJson()).stream().sorted().toList()
                ))
                .toList();
    }
    public List<DepartmentView> departments() {
        String tenantId = currentTenantId();
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
    public List<TenantView> tenants() {
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
                            TimeSupport.toEpochMilli(tenant.getExpireAt()),
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
        try {
            return DataScopeType.valueOf(scopeType);
        } catch (Exception ignored) {
            return DataScopeType.SELF;
        }
    }

    private Map<String, TenantProfile> loadTenantProfiles(List<SysTenantEntity> tenants) {
        if (tenants.isEmpty()) {
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
        ).stream().collect(Collectors.toMap(
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
        ).stream().collect(Collectors.toMap(
                SysTenantCapabilityEntity::getCapabilityCode,
                java.util.function.Function.identity(),
                (left, right) -> right,
                LinkedHashMap::new
        ));
        Map<String, List<String>> packageCapabilities = packageCodes.isEmpty() ? Map.of() : sysTenantPackageCapabilityMapper.selectList(
                new LambdaQueryWrapper<SysTenantPackageCapabilityEntity>()
                        .eq(SysTenantPackageCapabilityEntity::getTenantId, "platform")
                        .in(SysTenantPackageCapabilityEntity::getPackageCode, packageCodes)
        ).stream().collect(Collectors.groupingBy(
                SysTenantPackageCapabilityEntity::getPackageCode,
                LinkedHashMap::new,
                Collectors.mapping(SysTenantPackageCapabilityEntity::getCapabilityCode, Collectors.toList())
        ));
        Map<String, List<SysTenantCapabilityOverrideEntity>> overrides = sysTenantCapabilityOverrideMapper.selectList(
                new LambdaQueryWrapper<SysTenantCapabilityOverrideEntity>()
                        .in(SysTenantCapabilityOverrideEntity::getTenantId, tenantIds)
        ).stream().collect(Collectors.groupingBy(
                SysTenantCapabilityOverrideEntity::getTenantId,
                LinkedHashMap::new,
                Collectors.toList()
        ));
        Map<String, TenantProfile> result = new LinkedHashMap<>();
        for (SysTenantEntity tenant : tenants) {
            SysTenantPackageEntity pkg = packages.get(tenant.getPackageCode());
            List<String> capabilityCodes = new java.util.ArrayList<>(packageCapabilities.getOrDefault(tenant.getPackageCode(), List.of()));
            Map<String, String> descriptions = capabilityCodes.stream().collect(Collectors.toMap(
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
            @Schema(description = "到期时间") Long expireAt,
            @Schema(description = "套餐编码") String packageCode,
            @Schema(description = "套餐名称") String packageName,
            @Schema(description = "用户配额") Integer userQuota,
            @Schema(description = "存储配额(GB)") Integer storageQuotaGb,
            @Schema(description = "能力编码集合") List<String> capabilityCodes,
            @Schema(description = "能力说明映射") Map<String, String> capabilityDescriptions,
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
