package com.enterprise.auth.platform.config;

import com.enterprise.auth.platform.user.model.UserAccount;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.auth.platform.auth.DatabaseRegisteredClientRepository;
import com.enterprise.auth.platform.auth.service.AuditingAuthorizationConsentService;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.tenant.TenantFilter;
import com.enterprise.auth.platform.tenant.TenantProperties;
import com.enterprise.auth.platform.user.repository.UserRepository;
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
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
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
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;

@Configuration
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            TenantFilter tenantFilter
    ) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, authorizationServer -> authorizationServer
                        .authorizationEndpoint(endpoint -> endpoint.consentPage("/oauth2/consent"))
                        .oidc(Customizer.withDefaults()))
            .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServerConfigurer.getEndpointsMatcher()))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain loginSecurityFilterChain(
            HttpSecurity http,
            TenantFilter tenantFilter
    ) throws Exception {
        http.securityMatcher("/login", "/oauth2/consent")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").permitAll())
                .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public UserDetailsService authorizationServerUserDetailsService(
            UserRepository userRepository,
            TenantProperties tenantProperties
    ) {
        return username -> {
            String tenantId = TenantContext.getTenantId();
            if (!StringUtils.hasText(tenantId)) {
                tenantId = tenantProperties.platformTenantId();
            }
            return userRepository.findByUsername(tenantId, username)
                    .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(username));
        };
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(
            SecurityProperties securityProperties,
            SysOauthClientMapper sysOauthClientMapper,
            JdbcTemplate jdbcTemplate
    ) {
        return new DatabaseRegisteredClientRepository(sysOauthClientMapper, jdbcTemplate, securityProperties);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        JdbcOAuth2AuthorizationService authorizationService = new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
        ObjectMapper authorizationObjectMapper = authorizationObjectMapper();

        JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper authorizationRowMapper =
                new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(registeredClientRepository);
        authorizationRowMapper.setObjectMapper(authorizationObjectMapper);
        authorizationService.setAuthorizationRowMapper(authorizationRowMapper);

        JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper authorizationParametersMapper =
                new JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper();
        authorizationParametersMapper.setObjectMapper(authorizationObjectMapper);
        authorizationService.setAuthorizationParametersMapper(authorizationParametersMapper);

        return authorizationService;
    }

    private ObjectMapper authorizationObjectMapper() {
        ClassLoader classLoader = AuthorizationServerConfig.class.getClassLoader();
        List<Module> securityModules = SecurityJackson2Modules.getModules(classLoader);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(securityModules);
        objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        objectMapper.addMixIn(UserAccount.class, UserAccountMixin.class);
        allowLegacyImmutableCollections(objectMapper);
        return objectMapper;
    }

    private void allowLegacyImmutableCollections(ObjectMapper objectMapper) {
        // Compatibility for previously serialized rows that captured JDK immutable collection impl classes.
        String[] legacySetImplNames = {
                "java.util.ImmutableCollections$SetN",
                "java.util.ImmutableCollections$Set12"
        };
        for (String className : legacySetImplNames) {
            try {
                Class<?> implClass = Class.forName(className);
                objectMapper.addMixIn(implClass, TrustedSetMixin.class);
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
    private abstract static class TrustedSetMixin {
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
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = rsaKey();
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
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            UserAccount user = resolveUser(context);
            if (user == null) {
                return;
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

    private RSAKey rsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("生成授权服务 RSA 密钥失败。", ex);
        }
    }
}
