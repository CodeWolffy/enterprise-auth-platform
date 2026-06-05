package com.enterprise.auth.platform.modules.role.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RoleQueryFacade {

    private final SysRoleMapper sysRoleMapper;

    public RoleQueryFacade(SysRoleMapper sysRoleMapper) {
        this.sysRoleMapper = sysRoleMapper;
    }

    public Map<Long, String> loadRoleCodeMap(String tenantId) {
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getDeleted, 0)
                .select(SysRoleEntity::getId, SysRoleEntity::getRoleCode))
                .stream()
                .collect(Collectors.toMap(SysRoleEntity::getId, SysRoleEntity::getRoleCode, (a, b) -> b, LinkedHashMap::new));
    }

    public List<SysRoleEntity> listAll(String tenantId) {
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getDeleted, 0));
    }

    public Set<Long> listRoleIdsByCodes(String tenantId, Set<String> roleCodes) {
        if (!StringUtils.hasText(tenantId) || roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        Set<String> normalizedCodes = roleCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedCodes.isEmpty()) {
            return Set.of();
        }
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                        .eq(SysRoleEntity::getTenantId, tenantId)
                        .eq(SysRoleEntity::getDeleted, 0)
                        .in(SysRoleEntity::getRoleCode, normalizedCodes)
                        .select(SysRoleEntity::getId))
                .stream()
                .map(SysRoleEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public long countByCodes(String tenantId, Set<String> roleCodes) {
        if (!StringUtils.hasText(tenantId) || roleCodes == null || roleCodes.isEmpty()) {
            return 0;
        }
        Set<String> normalizedCodes = roleCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedCodes.isEmpty()) {
            return 0;
        }
        return sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getDeleted, 0)
                .in(SysRoleEntity::getRoleCode, normalizedCodes));
    }

    public long countByIds(String tenantId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return 0;
        return sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getDeleted, 0)
                .in(SysRoleEntity::getId, roleIds));
    }
}