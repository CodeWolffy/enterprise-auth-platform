package com.enterprise.auth.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.auth.platform.config.feature.FeatureToggleProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EnterpriseAuthPlatformApplicationTests {

    @Autowired
    private FeatureToggleProperties featureToggleProperties;

    @Test
    void contextLoadsWithReservedComponentsDisabled() {
        assertThat(featureToggleProperties.gatewayEnabled()).isFalse();
        assertThat(featureToggleProperties.nacosEnabled()).isFalse();
        assertThat(featureToggleProperties.mqEnabled()).isFalse();
        assertThat(featureToggleProperties.seataEnabled()).isFalse();
        assertThat(featureToggleProperties.jobEnabled()).isFalse();
        assertThat(featureToggleProperties.lokiEnabled()).isFalse();
    }
}
