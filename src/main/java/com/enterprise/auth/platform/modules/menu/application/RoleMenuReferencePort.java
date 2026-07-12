package com.enterprise.auth.platform.modules.menu.application;

/**
 * 角色-菜单引用查询端口：menu 删除校验用，由 role 模块实现，menu 不依赖 role 实现类。
 */
public interface RoleMenuReferencePort {

    long countMenuReferencesAcrossTenants(Long menuId);
}