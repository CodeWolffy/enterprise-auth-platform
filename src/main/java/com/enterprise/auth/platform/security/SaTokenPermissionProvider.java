package com.enterprise.auth.platform.security;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
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
        return currentTokenUser().or(() -> loadUser(loginId))
                .map(user -> new ArrayList<>(user.permissions()))
                .orElseGet(ArrayList::new);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return currentTokenUser().or(() -> loadUser(loginId))
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

    private java.util.Optional<UserAccount> currentTokenUser() {
        try {
            if (!StpUtil.isLogin()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.ofNullable((UserAccount) StpUtil.getTokenSession().get("testUser"));
        } catch (RuntimeException ignored) {
            return java.util.Optional.empty();
        }
    }
}
