package com.enterprise.auth.platform.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.authorization-server")
public record AuthorizationServerProperties(
        String issuer,
        List<Client> clients
) {

    public List<Client> resolvedClients() {
        return clients == null ? List.of() : clients;
    }

    public record Client(
            String clientId,
            String clientSecret,
            String clientName,
            List<String> redirectUris,
            List<String> scopes,
            List<String> grantTypes
    ) {
        public List<String> resolvedRedirectUris() {
            return redirectUris == null ? List.of() : redirectUris;
        }

        public List<String> resolvedScopes() {
            return scopes == null ? List.of() : scopes;
        }

        public List<String> resolvedGrantTypes() {
            return grantTypes == null ? List.of() : grantTypes;
        }
    }
}
