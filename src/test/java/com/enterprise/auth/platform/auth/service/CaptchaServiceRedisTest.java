package com.enterprise.auth.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.SecurityRedisProperties;
import com.enterprise.auth.platform.config.SecurityProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class CaptchaServiceRedisTest {

    @Test
    void createAndValidateShouldUseRedisWhenEnabled() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SecurityProperties securityProperties = new SecurityProperties(
                "change-me-to-a-32-char-secret-key-for-prod",
                Duration.ofMinutes(30),
                Duration.ofDays(7),
                Duration.ofMinutes(5),
                true
        );
        SecurityRedisProperties redisProperties = new SecurityRedisProperties(true, true, false, "eap:auth:");

        CaptchaService captchaService = new CaptchaService(securityProperties, redisProperties, redisTemplate, null);

        CaptchaService.CaptchaChallenge challenge = captchaService.create();

        ArgumentCaptor<String> answerCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("eap:auth:captcha:" + challenge.captchaId()), answerCaptor.capture(), eq(Duration.ofMinutes(5)));
        assertThat(challenge.previewCode()).isEqualTo(answerCaptor.getValue());

        when(valueOperations.getAndDelete("eap:auth:captcha:" + challenge.captchaId())).thenReturn(challenge.previewCode());
        captchaService.validate(challenge.captchaId(), challenge.previewCode());

        when(valueOperations.getAndDelete(any())).thenReturn(null);
        assertThatThrownBy(() -> captchaService.validate(challenge.captchaId(), challenge.previewCode()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码已过期");
    }
}
