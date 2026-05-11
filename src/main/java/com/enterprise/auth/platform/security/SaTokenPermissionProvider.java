package com.enterprise.auth.platform.security;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class SaTokenPermissionProvider implements StpInterface {

    private final ObjectProvider<UserRepository> userRepository;

    public SaTokenPermissionProvider(ObjectProvider<UserRepository> userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Optional<List<String>> sessionPermissions = currentTokenList("permissions");
        if (sessionPermissions.isPresent()) {
            return sessionPermissions.get();
        }
        return loadUser(loginId)
                .map(user -> new ArrayList<>(user.permissions()))
                .orElseGet(ArrayList::new);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Optional<List<String>> sessionRoles = currentTokenList("roles");
        if (sessionRoles.isPresent()) {
            return sessionRoles.get();
        }
        return loadUser(loginId)
                .map(user -> new ArrayList<>(user.roles()))
                .orElseGet(ArrayList::new);
    }

    private java.util.Optional<UserAccount> loadUser(Object loginId) {
        if (loginId == null) {
            return java.util.Optional.empty();
        }
        try {
            return userRepository.getObject().findById(Long.parseLong(String.valueOf(loginId)));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    private Optional<List<String>> currentTokenList(String key) {
        try {
            String tokenValue = StpUtil.getTokenValue();
            if (tokenValue == null || tokenValue.isBlank()) {
                return Optional.empty();
            }
            Object value = StpUtil.getTokenSessionByToken(tokenValue).get(key);
            if (value instanceof Collection<?> collection) {
                return Optional.of(collection.stream()
                        .map(String::valueOf)
                        .toList());
            }
            return Optional.empty();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
