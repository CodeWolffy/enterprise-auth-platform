package com.enterprise.auth.platform.common.context;

import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Shared tenant context settings used by module boundaries and persistence infrastructure. */
@ConfigurationProperties(prefix = "app.tenant")
public record TenantProperties(
        String headerName,
        String platformTenantId,
        boolean mybatisTenantEnabled,
        List<String> mybatisIgnoreTables
) {

    public List<String> resolvedIgnoreTables() {
        LinkedHashSet<String> defaults = new LinkedHashSet<>();
        defaults.add("sys_menu");
        defaults.add("sys_dict");
        defaults.add("sys_dict_value");
        defaults.add("sys_config");
        defaults.add("sys_tenant");
        defaults.add("sys_tenant_package");
        defaults.add("sys_category_rule");
        if (mybatisIgnoreTables != null) {
            defaults.addAll(mybatisIgnoreTables);
        }
        return List.copyOf(defaults);
    }
}
