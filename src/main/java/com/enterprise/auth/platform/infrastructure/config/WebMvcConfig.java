package com.enterprise.auth.platform.infrastructure.config;

import com.enterprise.auth.platform.common.web.RateLimitInterceptor;
import com.enterprise.auth.platform.infrastructure.security.SaTokenUserContextInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/",
            "/favicon.ico",
            "/.well-known/**",
            "/error",
            "/actuator/health",
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/password/reset/request",
            "/api/auth/password/reset/verify",
            "/api/auth/password/reset/confirm",
            "/api/auth/captcha",
            "/api/auth/captcha/verify",
            "/api/auth/register/options",
            "/api/files/public/**",
            // SSE 端点通过短期一次性 ticket 自行鉴权，跳过 SaToken 与用户上下文拦截器。
            "/api/notifications/stream"
    );

    private final ObjectProvider<RateLimitInterceptor> rateLimitInterceptor;
    private final SaTokenUserContextInterceptor userContextInterceptor;
    private final CorsProperties corsProperties;

    public WebMvcConfig(
            ObjectProvider<RateLimitInterceptor> rateLimitInterceptor,
            SaTokenUserContextInterceptor userContextInterceptor,
            CorsProperties corsProperties
    ) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.userContextInterceptor = userContextInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new cn.dev33.satoken.interceptor.SaInterceptor(handle -> {
                    if (!"OPTIONS".equalsIgnoreCase(cn.dev33.satoken.context.SaHolder.getRequest().getMethod())) {
                        cn.dev33.satoken.stp.StpUtil.checkLogin();
                    }
                }))
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_PATHS);
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_PATHS);
        rateLimitInterceptor.ifAvailable(interceptor ->
                registry.addInterceptor(interceptor).addPathPatterns("/api/**"));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> allowedOrigins = corsProperties.resolvedAllowedOrigins();
        if (allowedOrigins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "X-Tenant-Id")
                .exposedHeaders("Authorization", "X-Request-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}