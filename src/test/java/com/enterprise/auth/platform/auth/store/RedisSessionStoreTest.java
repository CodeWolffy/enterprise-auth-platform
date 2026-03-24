package com.enterprise.auth.platform.auth.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.auth.model.UserSession;
import com.enterprise.auth.platform.config.SecurityRedisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

class RedisSessionStoreTest {

    @Test
    void saveShouldWriteSessionPayloadAndUserIndex() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(objectMapper.writeValueAsString(any(UserSession.class))).thenReturn("payload");

        RedisSessionStore store = new RedisSessionStore(
                redisTemplate,
                objectMapper,
                new SecurityRedisProperties(true, true, false, "eap:auth:", "v2"),
                null
        );

        UserSession session = new UserSession(
                "s-1",
                1L,
                "admin",
                "platform",
                "127.0.0.1",
                "chrome",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Instant.now(),
                true
        );

        store.save(session);

        verify(valueOperations).set(eq("eap:auth:v2:session:by-id:s-1"), eq("payload"), any(Duration.class));
        verify(zSetOperations).add(eq("eap:auth:v2:session:user-index:1"), eq("s-1"), any(Double.class));
    }

    @Test
    void deactivateShouldPersistInactiveSession() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(valueOperations.get("eap:auth:v2:session:by-id:s-1")).thenReturn("payload");

        UserSession active = new UserSession(
                "s-1",
                1L,
                "admin",
                "platform",
                "127.0.0.1",
                "chrome",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Instant.now(),
                true
        );

        when(objectMapper.readValue("payload", UserSession.class)).thenReturn(active);
        when(objectMapper.writeValueAsString(any(UserSession.class))).thenReturn("updated");

        RedisSessionStore store = new RedisSessionStore(
                redisTemplate,
                objectMapper,
                new SecurityRedisProperties(true, true, false, "eap:auth:", "v2"),
                null
        );

        store.deactivate("s-1");

        ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
        verify(objectMapper).writeValueAsString(captor.capture());
        assertThat(captor.getValue().active()).isFalse();
    }
}
