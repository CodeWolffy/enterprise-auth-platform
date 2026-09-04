package com.enterprise.auth.platform.modules.role.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.iam.api.IamDeptQueryPort;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleMenuEntity;
import com.enterprise.auth.platform.modules.role.api.RoleAccessControlPort;
import com.enterprise.auth.platform.modules.role.api.RoleAuthorizationInvalidationPort;
import com.enterprise.auth.platform.modules.role.api.RoleTenantReferencePort;
import com.enterprise.auth.platform.modules.iam.api.IamRoleUserReferencePort;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMenuMapper;
import com.enterprise.auth.platform.modules.role.interfaces.CreateRoleRequest;
import com.enterprise.auth.platform.modules.role.application.RolePayloadCodec;
import com.enterprise.auth.platform.modules.menu.api.MenuGrantQueryPort;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RoleManagementService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final IamRoleUserReferencePort userReferences;
    private final IamDeptQueryPort deptQueryPort;
    private final RoleCatalogFacade roleCatalogFacade;
    private final RoleAuthorizationInvalidationPort authorizationInvalidation;
    private final RolePayloadCodec rolePayloadCodec;
    private final MenuGrantQueryPort menuGrantQueryPort;
    private final RoleAccessControlPort accessControl;
    private final RoleTenantReferencePort tenantReferences;

    public RoleManagementService(
            SysRoleMapper sysRoleMapper,
            SysRoleMenuMapper sysRoleMenuMapper,
            IamRoleUserReferencePort userReferences,
            IamDeptQueryPort deptQueryPort,
            RoleCatalogFacade roleCatalogFacade,
            RoleAuthorizationInvalidationPort authorizationInvalidation,
            RolePayloadCodec rolePayloadCodec,
            MenuGrantQueryPort menuGrantQueryPort,
            RoleAccessControlPort accessControl,
            RoleTenantReferencePort tenantReferences
    ) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.userReferences = userReferences;
        this.deptQueryPort = deptQueryPort;
        this.roleCatalogFacade = roleCatalogFacade;
        this.authorizationInvalidation = authorizationInvalidation;
        this.rolePayloadCodec = rolePayloadCodec;
        this.menuGrantQueryPort = menuGrantQueryPort;
        this.accessControl = accessControl;
        this.tenantReferences = tenantReferences;
    }

    @Transactional
    public RoleView create(CreateRoleRequest request) {
        String tenantId = resolveTargetTenantId(request.tenantId());
        if (existsRoleCode(tenantId, request.roleCode())) {
            throw new BusinessException("角色编码已存在");
        }

        SysRoleEntity entity = new SysRoleEntity();
        entity.setTenantId(tenantId);
        entity.setRoleCode(request.roleCode());
        entity.setRoleName(request.roleName());
        entity.setRoleDesc(request.roleDesc());
        applyDataScope(entity, tenantId, request.dataScopeType(), request.customDeptIds());
        sysRoleMapper.insert(entity);

        return roleCatalogFacade.tenantRole(tenantId, entity.getId());
    }

    @Transactional
    public RoleView update(Long roleId, CreateRoleRequest request) {
        SysRoleEntity entity = getRole(roleId);
        String tenantId = entity.getTenantId();
        entity.setRoleName(request.roleName());
        entity.setRoleDesc(request.roleDesc());
        applyDataScope(entity, tenantId, request.dataScopeType(), request.customDeptIds());
        sysRoleMapper.updateById(entity);
        evictPrincipalsByRole(tenantId, roleId);

        return roleCatalogFacade.tenantRole(tenantId, entity.getId());
    }

    @Transactional
    public Set<Long> assignMenus(Long roleId, Set<Long> menuIds) {
        SysRoleEntity entity = getRole(roleId);
        String tenantId = entity.getTenantId();
        Set<Long> assigned = menuGrantQueryPort.expandMenuIdsWithAncestors(tenantId, menuIds);
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenuEntity>()
                .eq(SysRoleMenuEntity::getTenantId, tenantId)
                .eq(SysRoleMenuEntity::getRoleId, roleId));
        if (!assigned.isEmpty()) {
            List<SysRoleMenuEntity> relations = assigned.stream()
                    .map(menuId -> roleMenu(tenantId, roleId, menuId))
                    .toList();
            sysRoleMenuMapper.insert(relations);
        }
        evictPrincipalsByRole(tenantId, roleId);
        return assigned;
    }

    private SysRoleMenuEntity roleMenu(String tenantId, Long roleId, Long menuId) {
        SysRoleMenuEntity relation = new SysRoleMenuEntity();
        relation.setTenantId(tenantId);
        relation.setRoleId(roleId);
        relation.setMenuId(menuId);
        return relation;
    }

    public Set<Long> listAssignedMenus(Long roleId) {
        SysRoleEntity entity = getRole(roleId);
        String tenantId = entity.getTenantId();
        return menuGrantQueryPort.filterGrantableMenuIds(tenantId, listRoleMenuIds(tenantId, roleId));
    }

    public RoleImpactView impact(Long roleId) {
        SysRoleEntity entity = getRole(roleId);
        String tenantId = entity.getTenantId();
        List<Long> assignedUserIds = userReferences.listUserIdsByRole(tenantId, roleId);
        Set<Long> assignedMenuIds = listRoleMenuIds(tenantId, roleId);
        boolean deleteBlocked = !assignedUserIds.isEmpty();
        List<String> warnings = new java.util.ArrayList<>();
        if (deleteBlocked) {
            warnings.add("角色已分配给用户，需先调整用户角色后才能删除。");
        }
        if (!assignedMenuIds.isEmpty()) {
            warnings.add("删除角色会同步移除该角色的菜单授权关系。");
        }
        if (warnings.isEmpty()) {
            warnings.add("当前未发现删除阻断项。");
        }
        return new RoleImpactView(
                entity.getId(),
                entity.getRoleCode(),
                entity.getRoleName(),
                assignedUserIds.size(),
                assignedUserIds.stream().limit(5).toList(),
                assignedMenuIds.size(),
                deleteBlocked,
                warnings
        );
    }

    @Transactional
    public void delete(Long roleId) {
        SysRoleEntity entity = getRole(roleId);
        String tenantId = entity.getTenantId();
        long assignedUsers = userReferences.countUsersByRole(tenantId, roleId);
        if (assignedUsers > 0) {
            throw new BusinessException("角色已分配给用户，暂不允许删除");
        }

        sysRoleMapper.deleteById(entity.getId());
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenuEntity>()
                .eq(SysRoleMenuEntity::getTenantId, tenantId)
                .eq(SysRoleMenuEntity::getRoleId, roleId));
        evictPrincipalsByRole(tenantId, roleId);
    }

    public Set<Long> listRoleMenuIds(String tenantId, Long roleId) {
        return sysRoleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenuEntity>()
                        .eq(SysRoleMenuEntity::getTenantId, tenantId)
                        .eq(SysRoleMenuEntity::getRoleId, roleId))
                .stream()
                .map(SysRoleMenuEntity::getMenuId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public Set<Long> listMenuIdsByRoleCodes(String tenantId, Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        List<Long> roleIds = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                        .eq(SysRoleEntity::getTenantId, tenantId)
                        .eq(SysRoleEntity::getDeleted, 0)
                        .in(SysRoleEntity::getRoleCode, roleCodes))
                .stream()
                .map(SysRoleEntity::getId)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return sysRoleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenuEntity>()
                        .eq(SysRoleMenuEntity::getTenantId, tenantId)
                        .in(SysRoleMenuEntity::getRoleId, roleIds))
                .stream()
                .map(SysRoleMenuEntity::getMenuId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private void applyDataScope(SysRoleEntity entity, String tenantId, DataScopeType scopeType, List<Long> customDeptIds) {
        entity.setDataScopeType(scopeType.name());
        if (scopeType != DataScopeType.CUSTOM) {
            entity.setDataScopeValueJson(null);
            return;
        }
        List<Long> normalizedDeptIds = customDeptIds == null ? List.of() : customDeptIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedDeptIds.isEmpty()) {
            throw new BusinessException("自定义数据范围至少选择一个部门");
        }
        long validCount = deptQueryPort.countByIds(tenantId, normalizedDeptIds);
        if (validCount != normalizedDeptIds.size()) {
            throw new BusinessException("存在无效的自定义部门");
        }
        entity.setDataScopeValueJson(rolePayloadCodec.writeDeptIds(normalizedDeptIds));
    }

    private void evictPrincipalsByRole(String tenantId, Long roleId) {
        List<Long> userIds = userReferences.listUserIdsByRole(tenantId, roleId);
        if (userIds.isEmpty()) {
            return;
        }
        var users = userReferences.findByIds(userIds);
        if (users.isEmpty()) {
            return;
        }
        for (var user : users) {
            authorizationInvalidation.invalidateUser(user.id(), user.tenantId(), user.username());
        }
    }

    private boolean existsRoleCode(String tenantId, String roleCode) {
        return sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getRoleCode, roleCode)
                .eq(SysRoleEntity::getDeleted, 0)) > 0;
    }

    private SysRoleEntity getRole(Long roleId) {
        SysRoleEntity entity;
        if (accessControl.isPlatformSuperAdmin()) {
            entity = InterceptorIgnoreHelper.execute(
                    IgnoreStrategy.builder().tenantLine(true).build(),
                    () -> sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRoleEntity>()
                            .eq(SysRoleEntity::getId, roleId)
                            .eq(SysRoleEntity::getDeleted, 0)
                            .last("limit 1"))
            );
        } else {
            entity = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRoleEntity>()
                    .eq(SysRoleEntity::getId, roleId)
                .eq(SysRoleEntity::getTenantId, TenantContextSupport.currentTenantIdOrPlatform())
                    .eq(SysRoleEntity::getDeleted, 0)
                    .last("limit 1"));
        }
        if (entity == null) {
            throw new BusinessException("角色不存在");
        }
        return entity;
    }

    public record RoleImpactView(
            Long roleId,
            String roleCode,
            String roleName,
            int assignedUserCount,
            List<Long> sampleUserIds,
            int assignedMenuCount,
            boolean deleteBlocked,
            List<String> warnings
    ) {
    }

    private String resolveTargetTenantId(String requestedTenantId) {
        String currentTenantId = TenantContextSupport.currentTenantIdOrPlatform();
        if (!accessControl.isPlatformSuperAdmin()) {
            return currentTenantId;
        }
        String targetTenantId = StringUtils.hasText(requestedTenantId) ? requestedTenantId.trim() : currentTenantId;
        if (!tenantReferences.tenantExists(targetTenantId)) {
            throw new BusinessException("TENANT_NOT_FOUND", "租户不存在");
        }
        return targetTenantId;
    }
}
