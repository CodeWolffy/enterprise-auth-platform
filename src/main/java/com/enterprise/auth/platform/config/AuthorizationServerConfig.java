package com.enterprise.auth.platform.config;

import com.enterprise.auth.platform.user.model.UserAccount;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.auth.platform.auth.DatabaseRegisteredClientRepository;
import com.enterprise.auth.platform.auth.service.AuthorizationSessionService;
import com.enterprise.auth.platform.auth.service.AuditingAuthorizationConsentService;
import com.enterprise.auth.platform.auth.service.LoginAttemptService;
import com.enterprise.auth.platform.auth.service.LoginAttemptService.LoginFailureResult;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.tenant.TenantFilter;
import com.enterprise.auth.platform.tenant.TenantProperties;
import com.enterprise.auth.platform.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            TenantFilter tenantFilter
    ) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        RequestMatcher authorizationEndpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();
        RequestMatcher csrfIgnoredEndpoints = request ->
                authorizationEndpointsMatcher.matches(request)
                        && !request.getRequestURI().endsWith("/oauth2/authorize");
        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, authorizationServer -> authorizationServer
                        .authorizationEndpoint(endpoint -> endpoint.consentPage("/oauth2/consent"))
                        .oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(csrfIgnoredEndpoints))
                .headers(this::applySecurityHeaders)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain loginSecurityFilterChain(
            HttpSecurity http,
            TenantFilter tenantFilter,
            AuthenticationSuccessHandler oauthLoginSuccessHandler,
            AuthenticationFailureHandler oauthLoginFailureHandler
    ) throws Exception {
        http.securityMatcher("/login", "/oauth2/consent")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(oauthLoginSuccessHandler)
                        .failureHandler(oauthLoginFailureHandler)
                        .permitAll())
                .headers(this::applySecurityHeaders)
                .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public UserDetailsService authorizationServerUserDetailsService(
            UserRepository userRepository,
            TenantProperties tenantProperties,
            LoginAttemptService loginAttemptService
    ) {
        return username -> {
            String tenantId = TenantContext.getTenantId();
            if (!StringUtils.hasText(tenantId)) {
                tenantId = tenantProperties.platformTenantId();
            }
            if (loginAttemptService.isLocked(tenantId, username)) {
                loginAttemptService.recordBlockedAttempt(tenantId, username, currentRequestClientIp());
                throw new LockedException("Account is locked");
            }
            return userRepository.findByUsername(tenantId, username)
                    .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(username));
        };
    }

    @Bean
    public AuthenticationSuccessHandler oauthLoginSuccessHandler(
            LoginAttemptService loginAttemptService,
            TenantProperties tenantProperties
    ) {
        SavedRequestAwareAuthenticationSuccessHandler delegate = new SavedRequestAwareAuthenticationSuccessHandler();
        return (request, response, authentication) -> {
            String tenantId = resolveTenantId(request, tenantProperties);
            String username = authentication == null ? null : authentication.getName();
            loginAttemptService.clearFailures(tenantId, username);
            delegate.onAuthenticationSuccess(request, response, authentication);
        };
    }

    @Bean
    public AuthenticationFailureHandler oauthLoginFailureHandler(
            LoginAttemptService loginAttemptService,
            TenantProperties tenantProperties
    ) {
        return (request, response, exception) -> {
            String tenantId = resolveTenantId(request, tenantProperties);
            String username = request.getParameter("username");
            String clientIp = resolveClientIp(request);

            if (exception instanceof LockedException || loginAttemptService.isLocked(tenantId, username)) {
                loginAttemptService.recordBlockedAttempt(tenantId, username, clientIp);
                response.sendRedirect(buildLoginFailureRedirect(request, "locked", tenantId));
                return;
            }

            LoginFailureResult result = loginAttemptService.recordFailure(tenantId, username, "bad_credentials", clientIp);
            String errorCode = result.locked() ? "locked" : "bad_credentials";
            response.sendRedirect(buildLoginFailureRedirect(request, errorCode, tenantId));
        };
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(
            SecurityProperties securityProperties,
            TenantProperties tenantProperties,
            SysOauthClientMapper sysOauthClientMapper,
            JdbcTemplate jdbcTemplate
    ) {
        return new DatabaseRegisteredClientRepository(sysOauthClientMapper, jdbcTemplate, securityProperties, tenantProperties);
    }

    private ObjectMapper authorizationObjectMapper() {
        ClassLoader classLoader = AuthorizationServerConfig.class.getClassLoader();
        List<Module> securityModules = SecurityJackson2Modules.getModules(classLoader);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(securityModules);
        objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        objectMapper.addMixIn(UserAccount.class, UserAccountMixin.class);
        allowTrustedValueTypes(objectMapper);
        return objectMapper;
    }

    @Bean
    public JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper authorizationRowMapper(
            RegisteredClientRepository registeredClientRepository
    ) {
        ObjectMapper authorizationObjectMapper = authorizationObjectMapper();
        JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper authorizationRowMapper =
                new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(registeredClientRepository);
        authorizationRowMapper.setObjectMapper(authorizationObjectMapper);
        return authorizationRowMapper;
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository,
            @Qualifier("authorizationRowMapper") JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper authorizationRowMapper
    ) {
        JdbcOAuth2AuthorizationService authorizationService = new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
        authorizationService.setAuthorizationRowMapper(authorizationRowMapper);

        JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper authorizationParametersMapper =
                new JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper();
        authorizationParametersMapper.setObjectMapper(authorizationObjectMapper());
        authorizationService.setAuthorizationParametersMapper(authorizationParametersMapper);

        return authorizationService;
    }
    private void allowTrustedValueTypes(ObjectMapper objectMapper) {
        objectMapper.addMixIn(Long.class, TrustedValueMixin.class);
        objectMapper.addMixIn(Integer.class, TrustedValueMixin.class);
        objectMapper.addMixIn(Boolean.class, TrustedValueMixin.class);
        objectMapper.addMixIn(Double.class, TrustedValueMixin.class);
        objectMapper.addMixIn(Float.class, TrustedValueMixin.class);

        // Compatibility for previously serialized rows that captured JDK immutable collection impl classes.
        String[] legacySetImplNames = {
                "java.util.ImmutableCollections$SetN",
                "java.util.ImmutableCollections$Set12"
        };
        for (String className : legacySetImplNames) {
            try {
                Class<?> implClass = Class.forName(className);
                objectMapper.addMixIn(implClass, TrustedValueMixin.class);
            } catch (ClassNotFoundException ignored) {
                // Different JDK may not expose all immutable implementation variants.
            }
        }
    }

        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
        @JsonIgnoreProperties(value = {
            "authorities",
            "accountNonExpired",
            "accountNonLocked",
            "credentialsNonExpired"
        }, ignoreUnknown = true)
    private abstract static class UserAccountMixin {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    private abstract static class TrustedValueMixin {
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository,
            SysOauthClientMapper sysOauthClientMapper,
            com.enterprise.auth.platform.audit.service.AuditService auditService
    ) {
        OAuth2AuthorizationConsentService delegate = new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
        return new AuditingAuthorizationConsentService(delegate, registeredClientRepository, sysOauthClientMapper, auditService);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(AuthorizationServerProperties properties) {
        RSAKey rsaKey = rsaKey(properties);
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (selector, context) -> selector.select(jwkSet);
    }

    @Bean
    public JwtDecoder authorizationServerJwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(AuthorizationServerProperties properties) {
        return AuthorizationServerSettings.builder()
                .issuer(properties.issuer())
                .build();
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(
            UserRepository userRepository,
            AuthorizationSessionService authorizationSessionService
    ) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            UserAccount snapshotUser = resolveUser(context);
            UserAccount user = resolveCurrentUser(snapshotUser, context.getAuthorization() == null ? null : context.getAuthorization().getId(),
                    userRepository, authorizationSessionService);
            if (user == null) {
                return;
            }
            if (context.getAuthorization() != null) {
                authorizationSessionService.activate(user, context.getAuthorization().getId());
            }
            context.getClaims().claim("uid", user.id());
            context.getClaims().claim("tenant", user.tenantId());
            context.getClaims().claim("roles", user.roles());
            context.getClaims().claim("permissions", user.permissions());
            context.getClaims().claim("custom_dept_ids", user.customDeptIds());
            context.getClaims().claim("data_scope", user.dataScopeType().name());
            context.getClaims().claim("ver", user.sessionVersion());
            context.getClaims().claim("sid", context.getAuthorization() == null ? "oauth2-access-token" : context.getAuthorization().getId());
            context.getClaims().claim("typ", "access");
        };
    }

    private UserAccount resolveCurrentUser(
            UserAccount snapshotUser,
            String authorizationId,
            UserRepository userRepository,
            AuthorizationSessionService authorizationSessionService
    ) {
        if (snapshotUser == null) {
            return null;
        }
        UserAccount currentUser = userRepository.findById(snapshotUser.id())
                .orElseThrow(() -> invalidGrant("user_not_found", authorizationId, authorizationSessionService));
        if (!currentUser.enabled()) {
            throw invalidGrant("user_disabled", authorizationId, authorizationSessionService);
        }
        if (currentUser.sessionVersion() != snapshotUser.sessionVersion()) {
            throw invalidGrant("session_version_changed", authorizationId, authorizationSessionService);
        }
        return currentUser;
    }

    private OAuth2AuthenticationException invalidGrant(
            String errorDescription,
            String authorizationId,
            AuthorizationSessionService authorizationSessionService
    ) {
        if (StringUtils.hasText(authorizationId)) {
            authorizationSessionService.revoke(authorizationId);
        }
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, errorDescription, null));
    }

    private UserAccount resolveUser(JwtEncodingContext context) {
        Authentication principal = context.getPrincipal();
        if (principal != null && principal.getPrincipal() instanceof UserAccount user) {
            return user;
        }
        if (context.getAuthorization() == null) {
            return null;
        }
        Object authenticationAttribute = context.getAuthorization().getAttribute(Authentication.class.getName());
        if (authenticationAttribute instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserAccount user) {
            return user;
        }
        Object principalAttribute = context.getAuthorization().getAttribute(Principal.class.getName());
        if (principalAttribute instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserAccount user) {
            return user;
        }
        return null;
    }

    private String resolveTenantId(HttpServletRequest request, TenantProperties tenantProperties) {
        String tenantId = request.getHeader(tenantProperties.headerName());
        if (!StringUtils.hasText(tenantId)) {
            tenantId = request.getParameter("tenantId");
        }
        if (!StringUtils.hasText(tenantId)) {
            tenantId = TenantContext.getTenantId();
        }
        return StringUtils.hasText(tenantId) ? tenantId : tenantProperties.platformTenantId();
    }

    private String buildLoginFailureRedirect(HttpServletRequest request, String errorCode, String tenantId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/login")
                .queryParam("error", errorCode);
        if (StringUtils.hasText(tenantId)) {
            builder.queryParam("tenantId", tenantId);
        }
        String clientId = request.getParameter("client_id");
        if (StringUtils.hasText(clientId)) {
            builder.queryParam("client_id", clientId);
        }
        return builder.build().toUriString();
    }

    private String currentRequestClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        return resolveClientIp(attributes.getRequest());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void applySecurityHeaders(HeadersConfigurer<HttpSecurity> headers) {
        headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; " +
                                "script-src 'self'; " +
                                "style-src 'self' 'unsafe-inline'; " +
                                "img-src 'self' data:; " +
                                "font-src 'self' data: https:; " +
                                "object-src 'none'; " +
                                "base-uri 'self'; " +
                                "frame-ancestors 'none'"))
                .referrerPolicy(policy -> policy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .permissionsPolicy(permissions -> permissions.policy("geolocation=(), camera=(), microphone=()"));
    }

    private RSAKey rsaKey(AuthorizationServerProperties properties) {
        try {
            Path jwkPath = Path.of(properties.resolvedJwkFile());
            if (Files.exists(jwkPath)) {
                return RSAKey.parse(Files.readString(jwkPath, StandardCharsets.UTF_8));
            }
            Path parent = jwkPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            RSAKey rsaKey = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(UUID.randomUUID().toString())
                    .build();
            Files.writeString(jwkPath, rsaKey.toJSONString(), StandardCharsets.UTF_8);
            return rsaKey;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize authorization server RSA key", ex);
        }
    }
}

