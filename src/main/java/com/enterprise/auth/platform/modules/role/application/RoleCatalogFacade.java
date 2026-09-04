package com.enterprise.auth.platform.modules.role.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.web.PaginationSupport;
import com.enterprise.auth.platform.modules.role.api.RoleAccessControlPort;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RoleCatalogFacade {

    private final SysRoleMapper sysRoleMapper;
    private final RolePayloadCodec rolePayloadCodec;
    private final RoleAccessControlPort accessControl;

    public RoleCatalogFacade(
            SysRoleMapper sysRoleMapper,
            RolePayloadCodec rolePayloadCodec,
            RoleAccessControlPort accessControl
    ) {
        this.sysRoleMapper = sysRoleMapper;
        this.rolePayloadCodec = rolePayloadCodec;
        this.accessControl = accessControl;
    }

    public List<SysRoleEntity> listRoles(String tenantId) {
        boolean globalScope = com.enterprise.auth.platform.common.context.TenantContext.isGlobalScope();
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(!globalScope, SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getDeleted, 0)
                .orderByAsc(SysRoleEntity::getTenantId)
                .orderByAsc(SysRoleEntity::getId));
    }

    public List<SysRoleEntity> listTenantRoles(String tenantId) {
        return com.enterprise.auth.platform.common.context.TenantContext.runWithTenant(tenantId, () ->
                sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                        .eq(SysRoleEntity::getTenantId, tenantId)
                        .eq(SysRoleEntity::getDeleted, 0)
                        .orderByAsc(SysRoleEntity::getId)));
    }

    public List<RoleItem> listRoleItems(String tenantId) {
        return listRoles(tenantId).stream()
                .map(r -> new RoleItem(r.getId(), r.getTenantId(), r.getRoleCode(), r.getRoleName(), r.getRoleDesc(),
                        r.getDataScopeType(), r.getDataScopeValueJson()))
                .toList();
    }

    public List<RoleItem> listTenantRoleItems(String tenantId) {
        return listTenantRoles(tenantId).stream()
                .map(r -> new RoleItem(r.getId(), r.getTenantId(), r.getRoleCode(), r.getRoleName(), r.getRoleDesc(),
                        r.getDataScopeType(), r.getDataScopeValueJson()))
                .toList();
    }

    public RolePayloadCodec payloadCodec() {
        return rolePayloadCodec;
    }

    public record RoleItem(Long id, String tenantId, String roleCode, String roleName, String roleDesc,
                           String dataScopeType, String dataScopeValueJson) {}

    public List<RoleView> roles() {
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform();
        boolean globalScope = TenantContext.isGlobalScope() || accessControl.isPlatformSuperAdmin();
        List<RoleItem> items = globalScope && !TenantContext.isGlobalScope()
                ? TenantContext.runWithGlobalScope(tenantId, () -> listRoleItems(tenantId))
                : listRoleItems(tenantId);
        return items.stream()
                .filter(role -> globalScope || tenantId.equals(role.tenantId()))
                .map(this::toView)
                .toList();
    }

    public PageResult<RoleView> rolesPage(
            String keyword, String dataScopeType, String tenantId, int page, int size) {
        int safePage = PaginationSupport.normalizePage(page);
        int safeSize = PaginationSupport.normalizeSize(size);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : null;
        String normalizedScope = StringUtils.hasText(dataScopeType) ? dataScopeType.trim() : null;
        String normalizedTenantId = StringUtils.hasText(tenantId) ? tenantId.trim() : null;
        List<RoleView> filtered = roles().stream()
                .filter(role -> normalizedTenantId == null || normalizedTenantId.equals(role.tenantId()))
                .filter(role -> normalizedScope == null || normalizedScope.equals(role.dataScopeType().name()))
                .filter(role -> normalizedKeyword == null
                        || contains(role.tenantId(), normalizedKeyword)
                        || contains(role.code(), normalizedKeyword)
                        || contains(role.name(), normalizedKeyword)
                        || contains(role.description(), normalizedKeyword))
                .toList();
        int from = Math.min((safePage - 1) * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        return PageResult.of(filtered.size(), safePage, safeSize, filtered.subList(from, to));
    }

    public List<RoleView> tenantRoles(String tenantId) {
        String normalizedTenantId = StringUtils.hasText(tenantId)
                ? tenantId : TenantContextSupport.currentTenantIdOrPlatform();
        return listTenantRoleItems(normalizedTenantId).stream().map(this::toView).toList();
    }

    public RoleView tenantRole(String tenantId, Long roleId) {
        return tenantRoles(tenantId).stream()
                .filter(role -> role.id().equals(roleId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("角色不存在"));
    }

    private RoleView toView(RoleItem role) {
        return new RoleView(
                role.id(), role.tenantId(), role.roleCode(), role.roleName(), role.roleDesc(),
                parseScope(role.dataScopeType()),
                rolePayloadCodec.readDeptIds(role.dataScopeValueJson()).stream().sorted().toList());
    }

    private DataScopeType parseScope(String scopeType) {
        if (!StringUtils.hasText(scopeType)) {
            return DataScopeType.SELF;
        }
        try {
            return DataScopeType.valueOf(scopeType);
        } catch (IllegalArgumentException exception) {
            return DataScopeType.SELF;
        }
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}
