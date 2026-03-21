package com.enterprise.auth.platform.tenant;

import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tenant")
public record TenantProperties(
        String headerName,
                String platformTenantId,
                boolean mybatisTenantEnabled,
                List<String> mybatisIgnoreTables
) {

        public List<String> resolvedIgnoreTables() {
                LinkedHashSet<String> defaults = new LinkedHashSet<>(List.of(
                                "oauth2_authorization",
                                "oauth2_authorization_consent"
                ));
                if (mybatisIgnoreTables != null) {
                        defaults.addAll(mybatisIgnoreTables);
                }
                return List.copyOf(defaults);
        }
}
