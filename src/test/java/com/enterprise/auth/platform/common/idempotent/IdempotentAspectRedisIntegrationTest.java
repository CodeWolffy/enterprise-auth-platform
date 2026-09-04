package com.enterprise.auth.platform.common.idempotent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.common.context.TenantContext;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.ObjectProvider;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdempotentAspectRedisIntegrationTest {

    private static final String TENANT_ID = "integration-tenant";
    private static final String REDIS_KEY = "eap:idempotent:" + TENANT_ID + ":slow:slow-request";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private RedissonClient redissonClient;
    private IdempotentAspect aspect;

    @BeforeAll
    void setUpClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379))
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(4);
        redissonClient = Redisson.create(config);

        @SuppressWarnings("unchecked")
        ObjectProvider<RedissonClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redissonClient);
        aspect = new IdempotentAspect(provider, "closed");
    }

    @AfterAll
    void tearDownClient() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @BeforeEach
    void setUpTenant() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDownTenant() {
        TenantContext.clear();
    }
    @Test
    void shouldAcquireAndReleaseARealRedisLock() throws Throwable {
        Method method = SampleService.class.getMethod("execute", String.class);
        Idempotent annotation = method.getAnnotation(Idempotent.class);
        ProceedingJoinPoint joinPoint = joinPoint(method, "order-1", "EXECUTED");

        Object result = aspect.around(joinPoint, annotation);

        assertThat(result).isEqualTo("EXECUTED");
        assertThat(redissonClient.getLock("eap:idempotent:" + TENANT_ID + ":integration:order-1").isLocked())
                .isFalse();
    }

    @Test
    void shouldRenewTheLeaseWhileARequestRunsLongerThanItsConfiguredLease() throws Throwable {
        Method method = SampleService.class.getMethod("slowExecute", String.class);
        Idempotent annotation = method.getAnnotation(Idempotent.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ProceedingJoinPoint firstJoinPoint = joinPoint(method, "slow-request", null);
        when(firstJoinPoint.proceed()).thenAnswer(invocation -> {
            started.countDown();
            assertThat(finish.await(5, TimeUnit.SECONDS)).isTrue();
            return "FIRST";
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> invoke(aspect, firstJoinPoint, annotation));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

            // The annotation uses a one-second lease to keep this regression test fast.
            // Without renewal, the contender would acquire the same lock here.
            Thread.sleep(1_200);

            ProceedingJoinPoint contender = joinPoint(method, "slow-request", "SECOND");
            Future<Object> second = executor.submit(() -> invoke(aspect, contender, annotation));
            assertThatThrownBy(() -> second.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(com.enterprise.auth.platform.common.exception.BusinessException.class);

            finish.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("FIRST");
        } finally {
            finish.countDown();
            RLock lock = redissonClient.getLock(REDIS_KEY);
            if (lock.isLocked()) {
                lock.forceUnlock();
            }
            executor.shutdownNow();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Object invoke(
            IdempotentAspect aspect, ProceedingJoinPoint joinPoint, Idempotent annotation) throws Exception {
        TenantContext.setTenantId(TENANT_ID);
        try {
            return aspect.around(joinPoint, annotation);
        } catch (Exception exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new AssertionError(throwable);
        } finally {
            TenantContext.clear();
        }
    }

    private ProceedingJoinPoint joinPoint(Method method, String id, Object proceedResult) throws Throwable {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{id});
        if (proceedResult != null) {
            when(joinPoint.proceed()).thenReturn(proceedResult);
        }
        return joinPoint;
    }

    static class SampleService {

        @Idempotent(prefix = "integration", key = "#id", timeout = 5, releaseOnSuccess = true)
        public String execute(String id) {
            return "EXECUTED-" + id;
        }

        @Idempotent(prefix = "slow", key = "#id", timeout = 1, releaseOnSuccess = false)
        public String slowExecute(String id) {
            return "SLOW-" + id;
        }
    }
}
