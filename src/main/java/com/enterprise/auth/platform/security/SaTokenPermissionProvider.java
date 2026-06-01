package com.enterprise.auth.platform.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.user.infrastructure.repository.UserRepository;
import com.enterprise.auth.platform.modules.resource.application.ResourceService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SaTokenPermissionProvider implements StpInterface {

    private final ObjectProvider<UserRepository> userRepository;
    private final ResourceService resourceService;
    private final PlatformAdminSupport platformAdminSupport;

    public SaTokenPermissionProvider(
            ObjectProvider<UserRepository> userRepository,
            ResourceService resourceService,
            PlatformAdminSupport platformAdminSupport
    ) {
        this.userRepository = userRepository;
        this.resourceService = resourceService;
        this.platformAdminSupport = platformAdminSupport;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Optional<List<String>> sessionPermissions = currentTokenSession()
                .map(session -> sessionStringList(session, "permissions"));
        if (sessionPermissions.isPresent()) {
            return sessionPermissions.get();
        }
        return loadUser(loginId)
                .map(user -> new ArrayList<>(resourceService.resolveGrantKeys(
                        activeTenantId(user),
                        user.roles(),
                        platformAdminSupport.isPlatformSuperAdmin(user)
                )))
                .orElseGet(ArrayList::new);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Optional<List<String>> sessionRoles = currentTokenSession()
                .map(session -> sessionStringList(session, "roles"));
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
            long userId = Long.parseLong(String.valueOf(loginId));
            String loginTenantId = currentTokenSession()
                    .map(session -> sessionString(session, "tenantId"))
                    .orElse(null);
            return runWithTenant(loginTenantId, () -> userRepository.getObject().findById(userId));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    private Optional<SaSession> currentTokenSession() {
        try {
            String tokenValue = StpUtil.getTokenValue();
            if (!StringUtils.hasText(tokenValue)) {
                return Optional.empty();
            }
            return Optional.of(StpUtil.getTokenSessionByToken(tokenValue));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private <T> T runWithTenant(String tenantId, Supplier<T> supplier) {
        if (!StringUtils.hasText(tenantId)) {
            return supplier.get();
        }
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private List<String> sessionStringList(SaSession session, String key) {
        Object value = session.get(key);
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            iterable.forEach(item -> {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            });
            return values;
        }
        return null;
    }

    private String sessionString(SaSession session, String key) {
        Object value = session.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String activeTenantId(UserAccount user) {
        return currentTokenSession()
                .map(session -> {
                    String sessionTenantId = sessionString(session, "activeTenantId");
                    if (!StringUtils.hasText(sessionTenantId)) {
                        sessionTenantId = sessionString(session, "tenantId");
                    }
                    String effectiveTenantId = platformAdminSupport.resolveEffectiveTenant(user, sessionTenantId);
                    if (!effectiveTenantId.equals(sessionString(session, "activeTenantId"))) {
                        session.set("activeTenantId", effectiveTenantId);
                    }
                    return effectiveTenantId;
                })
                .orElseGet(user::tenantId);
    }
}
