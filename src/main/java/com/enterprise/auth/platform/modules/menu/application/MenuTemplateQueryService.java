package com.enterprise.auth.platform.modules.menu.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.cache.CacheNames;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.menu.infrastructure.entity.SysMenuEntity;
import com.enterprise.auth.platform.modules.menu.infrastructure.mapper.SysMenuMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.TenantProperties;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 菜单模板的缓存查询入口。
 *
 * <p>独立于 {@link MenuService}，确保服务内部读取模板时也会经过 Spring 缓存代理。</p>
 */
@Service
public class MenuTemplateQueryService {

    private static final String DEFAULT_PLATFORM_TENANT = "platform";

    private final SysMenuMapper sysMenuMapper;
    private final TenantProperties tenantProperties;

    public MenuTemplateQueryService(SysMenuMapper sysMenuMapper, TenantProperties tenantProperties) {
        this.sysMenuMapper = sysMenuMapper;
        this.tenantProperties = tenantProperties;
    }

    @Cacheable(value = CacheNames.MENU_TEMPLATE, unless = "#result.isEmpty()")
    public List<SysMenuEntity> listTemplateMenus() {
        return TenantContext.runWithTenant(platformTenantId(), () ->
                sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenuEntity>()
                        .eq(SysMenuEntity::getDeleted, 0)
                        .orderByAsc(SysMenuEntity::getSort)
                        .orderByAsc(SysMenuEntity::getId)));
    }

    private String platformTenantId() {
        return StringUtils.hasText(tenantProperties.platformTenantId())
                ? tenantProperties.platformTenantId()
                : DEFAULT_PLATFORM_TENANT;
    }
}
