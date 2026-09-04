package com.enterprise.auth.platform.common.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 接口幂等防重注解。
 * <p>
 * 基于分布式锁 (Redisson) 实现高并发防重放提交。
 * 支持 SpEL 表达式动态解析参数值拼接幂等唯一 key。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 业务标识前缀，默认为空。
     */
    String prefix() default "";

    /**
     * 幂等 key 的 SpEL 表达式。
     * 例如：#request.orderNo 或 #id
     * 若为空，则默认根据方法签名和入参参数哈希生成。
     */
    String key() default "";

    /**
     * 锁过期 / 保持时间，防止极端情况下节点宕机导致死锁。
     * 默认 5 秒。
     */
    long timeout() default 5;

    /**
     * 时间单位，默认秒。
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 等待获取锁的最大时间（默认 0，即立即失败，不排队）。
     */
    long waitTime() default 0;

    /**
     * 重复提交时的提示消息。
     */
    String message() default "请勿重复提交，请稍候重试";

    /**
     * 是否在方法正常执行完毕后立即释放锁。
     * 默认 false（在 timeout 周期内保持锁，以实现窗口期防重）。
     * 若设置为 true，则方法执行完后立即释放锁（互斥模式）。
     */
    boolean releaseOnSuccess() default false;

    /**
     * 是否将当前租户隔离进幂等 key 中。默认 true。
     */
    boolean withTenant() default true;
}
