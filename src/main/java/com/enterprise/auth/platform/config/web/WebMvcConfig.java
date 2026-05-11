package com.enterprise.auth.platform.config.web;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.web.RateLimitInterceptor;
import com.enterprise.auth.platform.config.rate.RateLimitProperties;
import com.enterprise.auth.platform.security.SaTokenUserContextInterceptor;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/",
            "/favicon.ico",
            "/.well-known/**",
            "/error",
            "/actuator/health",
            "/doc.html",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/captcha",
            "/api/auth/captcha/verify",
            "/api/auth/register/options"
    );

    private final ObjectProvider<RateLimitInterceptor> rateLimitInterceptor;
    private final SaTokenUserContextInterceptor userContextInterceptor;
    private final FrontendProperties frontendProperties;

    public WebMvcConfig(
            ObjectProvider<RateLimitInterceptor> rateLimitInterceptor,
            SaTokenUserContextInterceptor userContextInterceptor,
            FrontendProperties frontendProperties
    ) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.userContextInterceptor = userContextInterceptor;
        this.frontendProperties = frontendProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    if (!"OPTIONS".equalsIgnoreCase(SaHolder.getRequest().getMethod())) {
                        StpUtil.checkLogin();
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
        List<String> allowedOrigins = frontendProperties.resolvedAllowedOrigins();
        if (allowedOrigins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
