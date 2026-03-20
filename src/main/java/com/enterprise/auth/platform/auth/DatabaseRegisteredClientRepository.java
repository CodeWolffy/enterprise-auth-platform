package com.enterprise.auth.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.config.SecurityProperties;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.StringUtils;

public class DatabaseRegisteredClientRepository implements RegisteredClientRepository {

    private final SysOauthClientMapper sysOauthClientMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SecurityProperties securityProperties;
    private final AtomicReference<Boolean> tableExists = new AtomicReference<>();

    public DatabaseRegisteredClientRepository(
            @Nullable SysOauthClientMapper sysOauthClientMapper,
            @Nullable JdbcTemplate jdbcTemplate,
            SecurityProperties securityProperties
    ) {
        this.sysOauthClientMapper = sysOauthClientMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.securityProperties = securityProperties;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        requireDatabase();
        SysOauthClientEntity existing = sysOauthClientMapper.selectIncludingDeleted("platform", registeredClient.getClientId());
        SysOauthClientEntity entity = existing == null ? new SysOauthClientEntity() : existing;
        entity.setTenantId("platform");
        entity.setClientId(registeredClient.getClientId());
        entity.setClientSecret(registeredClient.getClientSecret());
        entity.setClientName(registeredClient.getClientName());
        entity.setRedirectUris(String.join(",", registeredClient.getRedirectUris()));
        entity.setScopes(String.join(",", registeredClient.getScopes()));
        entity.setGrantTypes(registeredClient.getAuthorizationGrantTypes().stream()
                .map(AuthorizationGrantType::getValue)
                .reduce((a, b) -> a + "," + b)
                .orElse(""));
        entity.setRequirePkce(registeredClient.getClientSettings().isRequireProofKey() ? 1 : 0);
        entity.setRequireConsent(registeredClient.getClientSettings().isRequireAuthorizationConsent() ? 1 : 0);
        entity.setDeleted(0);
        if (existing == null) {
            sysOauthClientMapper.insert(entity);
            return;
        }
        sysOauthClientMapper.updateById(entity);
    }

    @Override
    public RegisteredClient findById(String id) {
        requireDatabase();
        return loadClients().stream()
                .filter(client -> client.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        requireDatabase();
        return loadClients().stream()
                .filter(client -> client.getClientId().equals(clientId))
                .findFirst()
                .orElse(null);
    }

    private List<RegisteredClient> loadClients() {
        return sysOauthClientMapper.selectList(new LambdaQueryWrapper<SysOauthClientEntity>()
                        .eq(SysOauthClientEntity::getDeleted, 0)
                        .orderByAsc(SysOauthClientEntity::getId))
                .stream()
                .map(this::fromEntity)
                .toList();
    }

    private void requireDatabase() {
        if (!databaseAvailable()) {
            throw new IllegalStateException("未检测到 OAuth2 客户端配置表 sys_oauth_client，请先初始化数据库脚本。");
        }
    }

    private boolean databaseAvailable() {
        Boolean cached = tableExists.get();
        if (cached != null) {
            return cached;
        }
        boolean available = sysOauthClientMapper != null && jdbcTemplate != null && detectTable();
        tableExists.compareAndSet(null, available);
        return available;
    }

    private boolean detectTable() {
        try {
            Integer count = jdbcTemplate.execute((Connection connection) -> {
                String catalog = connection.getCatalog();
                try (var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
                    statement.setString(1, catalog);
                    statement.setString(2, "sys_oauth_client");
                    try (var resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            return resultSet.getInt(1);
                        }
                        return 0;
                    }
                }
            });
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private RegisteredClient fromEntity(SysOauthClientEntity entity) {
        RegisteredClient.Builder builder = RegisteredClient.withId(
                        entity.getId() == null ? UUID.randomUUID().toString() : entity.getId().toString())
                .clientId(entity.getClientId())
                .clientName(entity.getClientName())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(securityProperties.accessTokenTtl())
                        .refreshTokenTimeToLive(securityProperties.refreshTokenTtl())
                        .reuseRefreshTokens(false)
                        .build())
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(entity.getRequirePkce() != null && entity.getRequirePkce() == 1)
                        .requireAuthorizationConsent(entity.getRequireConsent() != null && entity.getRequireConsent() == 1)
                        .build());
        if (StringUtils.hasText(entity.getClientSecret())) {
            builder.clientSecret(entity.getClientSecret())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }
        split(entity.getRedirectUris()).forEach(builder::redirectUri);
        split(entity.getScopes()).forEach(builder::scope);
        split(entity.getGrantTypes()).stream().map(this::grantType).forEach(builder::authorizationGrantType);
        return builder.build();
    }

    private List<String> split(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private AuthorizationGrantType grantType(String value) {
        return switch (value) {
            case "authorization_code" -> AuthorizationGrantType.AUTHORIZATION_CODE;
            case "refresh_token" -> AuthorizationGrantType.REFRESH_TOKEN;
            case "client_credentials" -> AuthorizationGrantType.CLIENT_CREDENTIALS;
            default -> new AuthorizationGrantType(value);
        };
    }
}
