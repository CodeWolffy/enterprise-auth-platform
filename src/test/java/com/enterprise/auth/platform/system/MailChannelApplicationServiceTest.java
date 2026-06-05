package com.enterprise.auth.platform.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.system.application.MailChannelApplicationService;
import com.enterprise.auth.platform.modules.system.application.MailChannelProperties;
import com.enterprise.auth.platform.modules.system.application.MailChannelSecretService;
import com.enterprise.auth.platform.modules.system.application.MailChannelSenderManager;
import com.enterprise.auth.platform.modules.system.application.TransactionalMailSupport;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysMailChannelEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysMailChannelMapper;
import com.enterprise.auth.platform.modules.system.interfaces.MailChannelRequest;
import com.enterprise.auth.platform.modules.tenant.infrastructure.TenantProperties;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

class MailChannelApplicationServiceTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void secretServiceShouldProtectStoredPasswordAndRequireKey() {
        MailChannelSecretService protectedSecretService = new MailChannelSecretService(new MailChannelProperties(
                "mail-secret-key-for-unit-test-2026",
                null,
                null,
                null,
                null
        ));

        String protectedValue = protectedSecretService.protect("smtp-raw-secret");

        assertThat(protectedValue).startsWith("{enc}v1:");
        assertThat(protectedValue).doesNotContain("smtp-raw-secret");
        assertThat(protectedSecretService.reveal(protectedValue)).isEqualTo("smtp-raw-secret");

        MailChannelSecretService missingKeyService = new MailChannelSecretService(new MailChannelProperties(
                "",
                null,
                null,
                null,
                null
        ));
        assertThatThrownBy(() -> missingKeyService.protect("smtp-raw-secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("APP_MAIL_SECRET_KEY");
    }

    @Test
    void saveChannelShouldNeverReturnOrStorePlainPassword() {
        TenantContext.setTenantId("tenant-a");
        SysMailChannelMapper mapper = mock(SysMailChannelMapper.class);
        MailChannelSenderManager senderManager = mock(MailChannelSenderManager.class);
        MailChannelApplicationService service = new MailChannelApplicationService(
                mapper,
                senderManager,
                new MailChannelSecretService(new MailChannelProperties(
                        "mail-secret-key-for-unit-test-2026",
                        null,
                        null,
                        null,
                        null
                )),
                new TransactionalMailSupport(),
                new TenantProperties("X-Tenant-Id", "platform", true, List.of())
        );
        when(mapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            SysMailChannelEntity entity = invocation.getArgument(0);
            entity.setId(1001L);
            return 1;
        }).when(mapper).insert(any(SysMailChannelEntity.class));

        var response = service.saveOrUpdate(new MailChannelRequest(
                "CUSTOM",
                "smtp.example.test",
                587,
                "smtp-user@example.test",
                "smtp-raw-secret",
                "noreply@example.test",
                "smtp",
                false,
                true,
                true
        ));

        ArgumentCaptor<SysMailChannelEntity> captor = ArgumentCaptor.forClass(SysMailChannelEntity.class);
        org.mockito.Mockito.verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getMailPassword()).startsWith("{enc}v1:");
        assertThat(captor.getValue().getMailPassword()).doesNotContain("smtp-raw-secret");
        assertThat(response.passwordConfigured()).isTrue();
        assertThat(response.toString()).doesNotContain("smtp-raw-secret");
    }

    @Test
    void sendTestMailShouldReturnGenericFailureWithoutLeakingProviderError() throws Exception {
        SysMailChannelMapper mapper = mock(SysMailChannelMapper.class);
        MailChannelSenderManager senderManager = mock(MailChannelSenderManager.class);
        TransactionalMailSupport mailSupport = mock(TransactionalMailSupport.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        SysMailChannelEntity config = enabledChannel();
        when(mapper.selectOne(any())).thenReturn(config);
        when(senderManager.getOrCreateSender(config)).thenReturn(sender);
        org.mockito.Mockito.doThrow(new RuntimeException("smtp password smtp-raw-secret rejected by smtp.example.test"))
                .when(mailSupport)
                .send(any(), any(), any(), any(), any());
        MailChannelApplicationService service = new MailChannelApplicationService(
                mapper,
                senderManager,
                new MailChannelSecretService(new MailChannelProperties(
                        "mail-secret-key-for-unit-test-2026",
                        null,
                        null,
                        null,
                        null
                )),
                mailSupport,
                new TenantProperties("X-Tenant-Id", "platform", true, List.of())
        );

        assertThatThrownBy(() -> service.sendTestMail("tenant-a", "receiver@example.test"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("MAIL_TEST_FAILED");
                    assertThat(exception.getMessage()).isEqualTo("测试邮件发送失败，请检查邮件渠道配置或稍后重试");
                    assertThat(exception.getMessage()).doesNotContain("smtp-raw-secret");
                    assertThat(exception.getMessage()).doesNotContain("smtp.example.test");
                });
    }

    private SysMailChannelEntity enabledChannel() {
        SysMailChannelEntity entity = new SysMailChannelEntity();
        entity.setId(2001L);
        entity.setTenantId("tenant-a");
        entity.setProvider("CUSTOM");
        entity.setMailHost("smtp.example.test");
        entity.setMailPort(587);
        entity.setMailUsername("smtp-user@example.test");
        entity.setMailPassword("{enc}v1:test");
        entity.setMailFrom("noreply@example.test");
        entity.setMailProtocol("smtp");
        entity.setUseSsl(0);
        entity.setUseStarttls(1);
        entity.setEnabled(1);
        entity.setDeleted(0);
        return entity;
    }
}