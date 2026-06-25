package com.enterprise.auth.platform.modules.log.infrastructure.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SysLog {

    /** 操作标题 */
    String value() default "";
}
