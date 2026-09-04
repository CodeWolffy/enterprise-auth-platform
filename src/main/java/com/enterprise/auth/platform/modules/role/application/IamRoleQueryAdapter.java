package com.enterprise.auth.platform.modules.role.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.iam.api.IamRoleQueryPort;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Exposes role-owned authorization data through the shared IAM contract. */
@Component
public final class IamRoleQueryAdapter implements IamRoleQueryPort {

    private static final Logger log = LoggerFactory.getLogger(IamRoleQueryAdapter.class);

    private final SysRoleMapper sysRoleMapper;
    private final RolePayloadCodec rolePayloadCodec;
    private final RoleGrantQueryFacade roleGrantQueryFacade;

    public IamRoleQueryAdapter(
            SysRoleMapper sysRoleMapper,
            RolePayloadCodec rolePayloadCodec,
            RoleGrantQueryFacade roleGrantQueryFacade
    ) {
        this.sysRoleMapper = sysRoleMapper;
        this.rolePayloadCodec = rolePayloadCodec;
        this.roleGrantQueryFacade = roleGrantQueryFacade;
    }

    @Override
    public RoleAuthorization resolveAuthorization(String tenantId, Set<Long> roleIds) {
        List<SysRoleEntity> roles = loadRolesByIds(tenantId, roleIds);
        if (roles.isEmpty()) {
            return new RoleAuthorization(Set.of(), Set.of(), Set.of(), DataScopeType.SELF);
        }
        Set<String> roleCodes = roles.stream()
                .map(SysRoleEntity::getRoleCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        DataScopeType dataScopeType = roles.stream()
                .map(SysRoleEntity::getDataScopeType)
                .map(this::parseScope)
                .max(Comparator.comparingInt(this::scopeWeight))
                .orElse(DataScopeType.SELF);
        Set<Long> customDeptIds = roles.stream()
                .filter(role -> parseScope(role.getDataScopeType()) == DataScopeType.CUSTOM)
                .flatMap(role -> rolePayloadCodec.readDeptIds(role.getDataScopeValueJson()).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean superAdmin = "platform".equals(tenantId) && roleCodes.contains("ADMIN");
        Set<String> permissionCodes = roleGrantQueryFacade.resolveGrantKeys(tenantId, roleCodes, superAdmin);
        return new RoleAuthorization(roleCodes, permissionCodes, customDeptIds, dataScopeType);
    }

    @Override
    public Map<Long, String> loadRoleCodeMap(String tenantId) {
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                        .eq(SysRoleEntity::getTenantId, tenantId)
                        .eq(SysRoleEntity::getDeleted, 0)
                        .select(SysRoleEntity::getId, SysRoleEntity::getRoleCode))
                .stream()
                .collect(Collectors.toMap(
                        SysRoleEntity::getId,
                        SysRoleEntity::getRoleCode,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    @Override
    public Set<String> resolveGrantKeys(String tenantId, Set<String> roleCodes, boolean superAdmin) {
        return roleGrantQueryFacade.resolveGrantKeys(tenantId, roleCodes, superAdmin);
    }

    @Override
    public Map<String, Long> loadRoleIdMap(String tenantId, Set<String> roleCodes) {
        if (!StringUtils.hasText(tenantId) || roleCodes == null || roleCodes.isEmpty()) {
            return Map.of();
        }
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                        .eq(SysRoleEntity::getTenantId, tenantId)
                        .eq(SysRoleEntity::getDeleted, 0)
                        .in(SysRoleEntity::getRoleCode, roleCodes)
                        .select(SysRoleEntity::getId, SysRoleEntity::getRoleCode))
                .stream()
                .collect(Collectors.toMap(
                        SysRoleEntity::getRoleCode,
                        SysRoleEntity::getId,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    @Override
    public List<RoleSummary> listRolesByIds(String tenantId, Set<Long> roleIds) {
        return loadRolesByIds(tenantId, roleIds).stream()
                .map(role -> new RoleSummary(
                        role.getId(),
                        role.getTenantId(),
                        role.getRoleCode(),
                        role.getRoleName(),
                        role.getRoleDesc(),
                        parseScope(role.getDataScopeType()),
                        rolePayloadCodec.readDeptIds(role.getDataScopeValueJson()).stream().sorted().toList()
                ))
                .toList();
    }

    private List<SysRoleEntity> loadRolesByIds(String tenantId, Set<Long> roleIds) {
        if (!StringUtils.hasText(tenantId) || roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getDeleted, 0)
                .in(SysRoleEntity::getId, roleIds)
                .orderByAsc(SysRoleEntity::getId));
    }

    private DataScopeType parseScope(String value) {
        if (!StringUtils.hasText(value)) {
            return DataScopeType.SELF;
        }
        try {
            return DataScopeType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            log.debug("未知的角色数据范围类型，回退为 SELF。value={}", value);
            return DataScopeType.SELF;
        }
    }

    private int scopeWeight(DataScopeType scopeType) {
        return switch (scopeType) {
            case SELF -> 1;
            case DEPT -> 2;
            case DEPT_AND_CHILDREN -> 3;
            case CUSTOM -> 4;
            case ALL -> 5;
        };
    }
}
