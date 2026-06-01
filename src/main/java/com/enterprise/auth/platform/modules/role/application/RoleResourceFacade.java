package com.enterprise.auth.platform.modules.role.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleResourceEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleResourceMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleResourceFacade {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleResourceMapper sysRoleResourceMapper;

    public RoleResourceFacade(SysRoleMapper sysRoleMapper, SysRoleResourceMapper sysRoleResourceMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleResourceMapper = sysRoleResourceMapper;
    }

    public Set<Long> listGrantedResourceIdsByRoleCodes(String tenantId, Set<String> roleCodes) {
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
        return sysRoleResourceMapper.selectList(new LambdaQueryWrapper<SysRoleResourceEntity>()
                        .eq(SysRoleResourceEntity::getTenantId, tenantId)
                        .in(SysRoleResourceEntity::getRoleId, roleIds))
                .stream()
                .map(SysRoleResourceEntity::getResourceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<Long> listRoleResourceIds(String tenantId, Long roleId) {
        return sysRoleResourceMapper.selectList(new LambdaQueryWrapper<SysRoleResourceEntity>()
                        .eq(SysRoleResourceEntity::getTenantId, tenantId)
                        .eq(SysRoleResourceEntity::getRoleId, roleId))
                .stream()
                .map(SysRoleResourceEntity::getResourceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional
    public Set<Long> assignRoleResources(String tenantId, Long roleId, Set<Long> requestedResourceIds, Set<Long> expandedResourceIds) {
        sysRoleResourceMapper.delete(new LambdaQueryWrapper<SysRoleResourceEntity>()
                .eq(SysRoleResourceEntity::getTenantId, tenantId)
                .eq(SysRoleResourceEntity::getRoleId, roleId));
        if (!expandedResourceIds.isEmpty()) {
            for (Long resourceId : expandedResourceIds) {
                SysRoleResourceEntity relation = new SysRoleResourceEntity();
                relation.setTenantId(tenantId);
                relation.setRoleId(roleId);
                relation.setResourceId(resourceId);
                sysRoleResourceMapper.insert(relation);
            }
        }
        return expandedResourceIds;
    }

    public long countResourceAssignments(Long resourceId) {
        return sysRoleResourceMapper.selectCount(new LambdaQueryWrapper<SysRoleResourceEntity>()
                .eq(SysRoleResourceEntity::getResourceId, resourceId));
    }
}