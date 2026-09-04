package com.enterprise.auth.platform.common.idempotent;

import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentAspectTest {

    private RedissonClient redissonClient;
    private RLock lock;
    private IdempotentAspect aspect;

    @BeforeEach
    void setUp() {
        redissonClient = Mockito.mock(RedissonClient.class);
        lock = Mockito.mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);

        @SuppressWarnings("unchecked")
        ObjectProvider<RedissonClient> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redissonClient);

        aspect = new IdempotentAspect(provider);
        TenantContext.setTenantId("tenant-test");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    public static class SampleService {
        @Idempotent(prefix = "order", key = "#id", timeout = 10, releaseOnSuccess = true)
        public String processOrder(String id) {
            return "SUCCESS-" + id;
        }
    }

    @Test
    void shouldProceedWhenLockAcquiredSuccessfully() throws Throwable {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        ProceedingJoinPoint joinPoint = Mockito.mock(ProceedingJoinPoint.class);
        Method method = SampleService.class.getMethod("processOrder", String.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);

        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"12345"});
        when(joinPoint.proceed()).thenReturn("SUCCESS-12345");

        Idempotent annotation = method.getAnnotation(Idempotent.class);
        Object result = aspect.around(joinPoint, annotation);

        assertThat(result).isEqualTo("SUCCESS-12345");
        verify(redissonClient).getLock("eap:idempotent:tenant-test:order:12345");
        verify(lock).tryLock(0, 10, TimeUnit.SECONDS);
        verify(lock).unlock();
    }

    @Test
    void shouldThrowConflictWhenLockCannotBeAcquired() throws Exception {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        ProceedingJoinPoint joinPoint = Mockito.mock(ProceedingJoinPoint.class);
        Method method = SampleService.class.getMethod("processOrder", String.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);

        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"12345"});

        Idempotent annotation = method.getAnnotation(Idempotent.class);

        assertThatThrownBy(() -> aspect.around(joinPoint, annotation))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请勿重复提交");
    }

    @Test
    void shouldBypassGracefullyWhenRedissonClientUnavailable() throws Throwable {
        @SuppressWarnings("unchecked")
        ObjectProvider<RedissonClient> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        IdempotentAspect bypassAspect = new IdempotentAspect(provider);

        ProceedingJoinPoint joinPoint = Mockito.mock(ProceedingJoinPoint.class);
        Method method = SampleService.class.getMethod("processOrder", String.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed()).thenReturn("BYPASS-SUCCESS");

        Idempotent annotation = method.getAnnotation(Idempotent.class);
        Object result = bypassAspect.around(joinPoint, annotation);

        assertThat(result).isEqualTo("BYPASS-SUCCESS");
    }
}
