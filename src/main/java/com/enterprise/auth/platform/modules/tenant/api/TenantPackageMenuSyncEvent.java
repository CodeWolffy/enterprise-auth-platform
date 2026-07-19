package com.enterprise.auth.platform.modules.tenant.api;

/**
 * Requests an idempotent rebuild of menus for every tenant currently referencing a package.
 */
public record TenantPackageMenuSyncEvent(String packageCode) {

    public static final String TYPE = "TENANT_PACKAGE_MENU_SYNC";
    public static final String AGGREGATE_TYPE = "TENANT_PACKAGE";
}
