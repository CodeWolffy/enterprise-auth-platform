package com.enterprise.auth.platform.user.model;

import com.enterprise.auth.platform.common.model.DataScopeType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashSet;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserAccount(
        Long id,
        String tenantId,
        String username,
        String password,
        boolean enabled,
        Set<String> roles,
        Set<String> permissions,
        Set<Long> customDeptIds,
        DataScopeType dataScopeType,
        int sessionVersion
) implements UserDetails {

    public UserAccount {
        roles = roles == null ? new HashSet<>() : new HashSet<>(roles);
        permissions = permissions == null ? new HashSet<>() : new HashSet<>(permissions);
        customDeptIds = customDeptIds == null ? new HashSet<>() : new HashSet<>(customDeptIds);
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Stream.concat(
                roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)),
                permissions.stream().map(SimpleGrantedAuthority::new)
        ).toList();
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return enabled;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
