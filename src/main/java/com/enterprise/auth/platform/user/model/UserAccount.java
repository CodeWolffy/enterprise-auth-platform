package com.enterprise.auth.platform.user.model;

import com.enterprise.auth.platform.common.model.DataScopeType;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

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

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Stream.concat(
                roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)),
                permissions.stream().map(SimpleGrantedAuthority::new)
        ).toList();
    }

    @Override
    public boolean isAccountNonExpired() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
