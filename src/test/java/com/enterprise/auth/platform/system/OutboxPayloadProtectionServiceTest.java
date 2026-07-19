package com.enterprise.auth.platform.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.auth.platform.modules.system.application.OutboxPayloadProtectionService;
import com.enterprise.auth.platform.modules.system.application.OutboxProperties;
import com.enterprise.auth.platform.modules.system.application.OutboxWriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

class OutboxPayloadProtectionServiceTest {

    private static final String KEY = "test-outbox-payload-secret-key-32-chars";

    @Test
    void passwordResetPayloadShouldRoundTripWithoutPersistingRawToken() {
        OutboxPayloadProtectionService service = service(KEY, new MockEnvironment());
        String payload = "{\"resetLink\":\"https://example/reset?token=raw-secret-token\"}";

        String protectedPayload = service.protect(OutboxWriter.TYPE_PASSWORD_RESET_MAIL, payload);

        assertThat(protectedPayload).startsWith("{enc}outbox:v1:");
        assertThat(protectedPayload).doesNotContain("raw-secret-token");
        assertThat(service.reveal(OutboxWriter.TYPE_PASSWORD_RESET_MAIL, protectedPayload)).isEqualTo(payload);
    }

    @Test
    void ciphertextShouldBeBoundToEventType() {
        OutboxPayloadProtectionService service = service(KEY, new MockEnvironment());
        String protectedPayload = service.protect(OutboxWriter.TYPE_PASSWORD_RESET_MAIL, "{\"token\":\"secret\"}");

        assertThatThrownBy(() -> service.reveal(OutboxWriter.TYPE_NOTIFICATION_PUBLISH, protectedPayload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decryption failed");
    }

    @Test
    void productionMustFailClosedWhenKeyIsMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> service("", environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_OUTBOX_PAYLOAD_SECRET_KEY");
    }

    @Test
    void nonSensitivePayloadShouldRemainReadableWithoutKey() {
        OutboxPayloadProtectionService service = service("", new MockEnvironment());
        String payload = "{\"title\":\"notice\"}";

        assertThat(service.protect(OutboxWriter.TYPE_NOTIFICATION_PUBLISH, payload)).isEqualTo(payload);
        assertThat(service.reveal(OutboxWriter.TYPE_NOTIFICATION_PUBLISH, payload)).isEqualTo(payload);
    }

    @Test
    void ordinaryApplicationContextShouldStartWithoutKey() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfiguration.class)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(OutboxPayloadProtectionService.class));
    }

    private OutboxPayloadProtectionService service(String key, MockEnvironment environment) {
        return new OutboxPayloadProtectionService(new OutboxProperties(2000L, key), environment);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OutboxProperties.class)
    static class TestConfiguration {

        @Bean
        OutboxPayloadProtectionService outboxPayloadProtectionService(
                OutboxProperties properties,
                Environment environment
        ) {
            return new OutboxPayloadProtectionService(properties, environment);
        }
    }
}
