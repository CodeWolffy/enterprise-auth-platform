package com.enterprise.auth.platform.modules.menu.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.menu.infrastructure.entity.SysMenuEntity;
import com.enterprise.auth.platform.modules.menu.infrastructure.mapper.SysMenuMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MenuTemplateQueryFacade {

    private final SysMenuMapper sysMenuMapper;

    public MenuTemplateQueryFacade(SysMenuMapper sysMenuMapper) {
        this.sysMenuMapper = sysMenuMapper;
    }

    public List<MenuTemplateItem> listActiveTemplateMenus() {
        return TenantContext.runWithTenant("platform", () -> sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenuEntity>()
                        .eq(SysMenuEntity::getDelFlag, "0")
                        .orderByAsc(SysMenuEntity::getSort)
                        .orderByAsc(SysMenuEntity::getId)))
                .stream()
                .map(menu -> new MenuTemplateItem(menu.getId(), menu.getParentId(), menu.getApplicationKey()))
                .toList();
    }

    public record MenuTemplateItem(Long id, Long parentId, String applicationKey) {
    }
}
