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

    public Optional<MenuTemplateNode> findByKey(String tenantId, String resourceKey) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(resourceKey)) {
            return Optional.empty();
        }
        return withTenant(tenantId, () -> Optional.ofNullable(sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getTenantId, tenantId)
                .eq(SysMenuEntity::getResourceKey, resourceKey)
                .eq(SysMenuEntity::getDeleted, 0)
                .last("limit 1")))
                .map(MenuTemplateNode::from));
    }

    public Optional<MenuTemplateNode> findByKeyAndType(String tenantId, String resourceKey, String menuType) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(resourceKey) || !StringUtils.hasText(menuType)) {
            return Optional.empty();
        }
        return withTenant(tenantId, () -> Optional.ofNullable(sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getTenantId, tenantId)
                .eq(SysMenuEntity::getResourceKey, resourceKey)
                .eq(SysMenuEntity::getMenuType, menuType)
                .eq(SysMenuEntity::getDeleted, 0)
                .last("limit 1")))
                .map(MenuTemplateNode::from));
    }

    public Optional<MenuTemplateNode> findById(String tenantId, Long menuId) {
        if (!StringUtils.hasText(tenantId) || menuId == null) {
            return Optional.empty();
        }
        return withTenant(tenantId, () -> Optional.ofNullable(sysMenuMapper.selectById(menuId))
                .filter(entity -> entity.getDeleted() == null || entity.getDeleted() == 0)
                .map(MenuTemplateNode::from));
    }

    public Long create(MenuTemplateMutation mutation) {
        SysMenuEntity entity = new SysMenuEntity();
        entity.setTenantId(mutation.tenantId());
        entity.setParentId(mutation.parentId());
        entity.setAncestors(mutation.ancestors());
        entity.setMenuType(mutation.menuType());
        entity.setResourceKey(mutation.resourceKey());
        entity.setMenuName(mutation.menuName());
        entity.setRouteKey(mutation.routeKey());
        entity.setGrantKey(mutation.grantKey());
        entity.setPath(mutation.path());
        entity.setComponent(mutation.component());
        entity.setIcon(mutation.icon());
        entity.setOrderNo(mutation.orderNo());
        entity.setVisible(mutation.visible());
        entity.setEnabled(mutation.enabled());
        entity.setIsSystem(mutation.system());
        withTenant(mutation.tenantId(), () -> {
            sysMenuMapper.insert(entity);
            return null;
        });
        return entity.getId();
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
            String ancestors,
            String menuType,
            String resourceKey
    ) {
        private static MenuTemplateNode from(SysMenuEntity entity) {
            return new MenuTemplateNode(
                    entity.getId(),
                    entity.getParentId(),
                    entity.getAncestors(),
                    entity.getMenuType(),
                    entity.getResourceKey()
            );
        }
    }

    public record MenuTemplateMutation(
            String tenantId,
            Long parentId,
            String ancestors,
            String menuType,
            String resourceKey,
            String menuName,
            String routeKey,
            String grantKey,
            String path,
            String component,
            String icon,
            Integer orderNo,
            Integer visible,
            Integer enabled,
            Integer system
    ) {
    }
}