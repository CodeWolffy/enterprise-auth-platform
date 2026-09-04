package com.enterprise.auth.platform.modules.menu.api;

/** Menu-owned contract for validating role references before menu deletion. */
public interface RoleMenuReferencePort {

    long countMenuReferencesAcrossTenants(Long menuId);
}
