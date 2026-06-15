package com.enterprise.auth.platform.modules.menu.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.menu.infrastructure.entity.SysMenuEntity;
import com.enterprise.auth.platform.modules.menu.infrastructure.mapper.SysMenuMapper;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MenuTemplateMutationFacade {

    private final SysMenuMapper sysMenuMapper;

    public MenuTemplateMutationFacade(SysMenuMapper sysMenuMapper) {
        this.sysMenuMapper = sysMenuMapper;
    }

    public Optional<MenuTemplateNode> findByKey(String tenantId, String key) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(key)) {
            return Optional.empty();
        }
        String normalizedKey = key.trim();
        return withTenant(tenantId, () -> Optional.ofNullable(sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getDelFlag, "0")
                .and(wrapper -> wrapper.eq(SysMenuEntity::getPath, normalizedKey).or().eq(SysMenuEntity::getPermission, normalizedKey))
                .last("limit 1")))
                .map(MenuTemplateNode::from));
    }

    public Optional<MenuTemplateNode> findByKeyAndType(String tenantId, String key, String type) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(key) || !StringUtils.hasText(type)) {
            return Optional.empty();
        }
        String normalizedType = normalizeType(type);
        String normalizedKey = key.trim();
        return withTenant(tenantId, () -> Optional.ofNullable(sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getDelFlag, "0")
                .eq(SysMenuEntity::getType, normalizedType)
                .and(wrapper -> wrapper.eq(SysMenuEntity::getPath, normalizedKey).or().eq(SysMenuEntity::getPermission, normalizedKey))
                .last("limit 1")))
                .map(MenuTemplateNode::from));
    }

    public Optional<MenuTemplateNode> findById(String tenantId, Long menuId) {
        if (!StringUtils.hasText(tenantId) || menuId == null) {
            return Optional.empty();
        }
        return withTenant(tenantId, () -> Optional.ofNullable(sysMenuMapper.selectById(menuId))
                .filter(entity -> entity.getDelFlag() == null || "0".equals(entity.getDelFlag()))
                .map(MenuTemplateNode::from));
    }

    public Long create(MenuTemplateMutation mutation) {
        SysMenuEntity entity = new SysMenuEntity();
        entity.setParentId(mutation.parentId());
        entity.setType(normalizeType(mutation.type()));
        entity.setName(mutation.name());
        entity.setPermission(mutation.permission());
        entity.setPath(mutation.path());
        entity.setComponent(mutation.component());
        entity.setIcon(mutation.icon());
        entity.setSort(mutation.sort() == null ? 0 : mutation.sort());
        entity.setOuterStatus(0);
        entity.setDelFlag("0");
        withTenant(mutation.tenantId(), () -> {
            sysMenuMapper.insert(entity);
            return null;
        });
        return entity.getId();
    }

    private static String normalizeType(String type) {
        if ("1".equals(type) || "BUTTON".equalsIgnoreCase(type) || "API".equalsIgnoreCase(type)) {
            return "1";
        }
        return "0";
    }

    private <T> T withTenant(String tenantId, Supplier<T> supplier) {
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    public record MenuTemplateNode(
            Long id,
            Long parentId,
            String path,
            String type,
            String permission
    ) {
        private static MenuTemplateNode from(SysMenuEntity entity) {
            return new MenuTemplateNode(
                    entity.getId(),
                    entity.getParentId(),
                    entity.getPath(),
                    entity.getType(),
                    entity.getPermission()
            );
        }
    }

    public record MenuTemplateMutation(
            String tenantId,
            Long parentId,
            String type,
            String name,
            String permission,
            String path,
            String component,
            String icon,
            Integer sort
    ) {
    }
}