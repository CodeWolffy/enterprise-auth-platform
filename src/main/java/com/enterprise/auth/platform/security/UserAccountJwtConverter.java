package com.enterprise.auth.platform.security;

import com.enterprise.auth.platform.auth.model.TokenClaims;
import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.user.model.UserAccount;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class UserAccountJwtConverter {

    public boolean supports(Jwt jwt) {
        return jwt.hasClaim("uid") && jwt.hasClaim("tenant");
    }

    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        UserAccount user = toUserAccount(jwt);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(user, jwt.getTokenValue(), user.getAuthorities());
        authentication.setDetails(toClaims(jwt));
        return authentication;
    }

    public UserAccount toUserAccount(Jwt jwt) {
        Long userId = claimLong(jwt, "uid");
        Integer sessionVersion = claimInt(jwt, "ver");
        String tenantId = jwt.getClaimAsString("tenant");
        String username = jwt.getSubject();
        Set<String> roles = new HashSet<>(claimStrings(jwt, "roles"));
        Set<String> permissions = new HashSet<>(claimStrings(jwt, "permissions"));
        Set<Long> customDeptIds = new HashSet<>(claimLongs(jwt, "custom_dept_ids"));
        String scopeValue = jwt.getClaimAsString("data_scope");
        DataScopeType dataScopeType = scopeValue == null ? DataScopeType.SELF : DataScopeType.valueOf(scopeValue);
        return new UserAccount(
                userId,
                tenantId,
                username,
                "",
                true,
                roles,
                permissions,
                customDeptIds,
                dataScopeType,
                sessionVersion == null ? 1 : sessionVersion
        );
    }

    public TokenClaims toClaims(Jwt jwt) {
        Long userId = claimLong(jwt, "uid");
        Integer sessionVersion = claimInt(jwt, "ver");
        return new TokenClaims(
                jwt.getId(),
                jwt.getClaimAsString("sid"),
                userId,
                jwt.getSubject(),
                jwt.getClaimAsString("tenant"),
                jwt.getClaimAsString("typ"),
                sessionVersion == null ? 1 : sessionVersion
        );
    }

    private List<String> claimStrings(Jwt jwt, String claimName) {
        List<String> values = jwt.getClaimAsStringList(claimName);
        return values == null ? List.of() : values;
    }

    private List<Long> claimLongs(Jwt jwt, String claimName) {
        List<?> values = jwt.getClaim(claimName);
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::longValue)
                .toList();
    }

    private Long claimLong(Jwt jwt, String claimName) {
        Number value = jwt.getClaim(claimName);
        return value == null ? null : value.longValue();
    }

    private Integer claimInt(Jwt jwt, String claimName) {
        Number value = jwt.getClaim(claimName);
        return value == null ? null : value.intValue();
    }
}
