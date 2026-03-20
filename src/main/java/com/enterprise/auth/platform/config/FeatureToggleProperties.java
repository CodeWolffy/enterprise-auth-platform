package com.enterprise.auth.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.features")
public record FeatureToggleProperties(
        boolean gatewayEnabled,
        boolean nacosEnabled,
        boolean mqEnabled,
        boolean seataEnabled,
        boolean jobEnabled,
        boolean lokiEnabled
) {
}

