package com.enterprise.auth.platform.infrastructure.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 有界异步执行器：审计 / 通知 / 默认异步任务隔离，避免 SimpleAsyncTaskExecutor 无线程复用。
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    public static final String AUDIT_EXECUTOR = "auditExecutor";
    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";
    public static final String MAIL_EXECUTOR = "mailExecutor";

    @Bean(name = AUDIT_EXECUTOR)
    public Executor auditExecutor() {
        return buildExecutor("audit-", 2, 4, 500);
    }

    @Bean(name = NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor() {
        return buildExecutor("notify-", 2, 8, 1000);
    }

    @Bean(name = MAIL_EXECUTOR)
    public Executor mailExecutor() {
        return buildExecutor("mail-", 1, 4, 200);
    }

    @Override
    public Executor getAsyncExecutor() {
        return buildExecutor("async-", 4, 8, 500);
    }

    private ThreadPoolTaskExecutor buildExecutor(String prefix, int core, int max, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}