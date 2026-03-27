package com.enterprise.auth.platform.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    String key() default "";

    int capacity() default -1;

    int refillTokens() default -1;

    long refillDurationSeconds() default -1;

    Strategy strategy() default Strategy.IP;

    String message() default "请求过于频繁，请稍后再试";

    enum Strategy {
        IP,
        USER,
        USER_AND_IP
    }
}
