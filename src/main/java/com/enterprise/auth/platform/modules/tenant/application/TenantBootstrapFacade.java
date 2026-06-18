package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import com.enterprise.auth.platform.modules.dept.application.DeptBootstrapFacade;
import com.enterprise.auth.platform.modules.menu.application.MenuService;
import com.enterprise.auth.platform.modules.menu.domain.MenuTreeNode;
import com.enterprise.auth.platform.modules.role.application.RoleBootstrapFacade;
import com.enterprise.auth.platform.modules.user.application.UserBootstrapFacade;
import com.enterprise.auth.platform.modules.user.application.UserBootstrapFacade.AdminUserResult;
import com.enterprise.auth.platform.modules.user.application.UserBootstrapFacade.EnsureAdminUserRequest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TenantBootstrapFacade {

    private static final Logger log = LoggerFactory.getLogger(TenantBootstrapFacade.class);
    private static final Pattern USERNAME_ALLOWED_CHARS = Pattern.compile("[^a-zA-Z0-9_.-]");

    private final DeptBootstrapFacade deptBootstrapFacade;
    private final RoleBootstrapFacade roleBootstrapFacade;
    private final UserBootstrapFacade userBootstrapFacade;
    private final PasswordHasher passwordHasher;
    private final MenuService menuService;
    private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();

    public TenantBootstrapFacade(
            DeptBootstrapFacade deptBootstrapFacade,
            RoleBootstrapFacade roleBootstrapFacade,
            UserBootstrapFacade userBootstrapFacade,
            PasswordHasher passwordHasher,
            MenuService menuService
    ) {
        this.deptBootstrapFacade = deptBootstrapFacade;
        this.roleBootstrapFacade = roleBootstrapFacade;
        this.userBootstrapFacade = userBootstrapFacade;
        this.passwordHasher = passwordHasher;
        this.menuService = menuService;
    }

    public BootstrapResult bootstrap(String tenantId, String tenantName, String operator) {
        if (!StringUtils.hasText(tenantId)) {
            return BootstrapResult.empty();
        }
        return withTenant(tenantId, () -> {
            Long rootDeptId = deptBootstrapFacade.ensureRootDept(tenantId, tenantName);
            Long adminRoleId = roleBootstrapFacade.ensureTenantAdminRole(tenantId);
            Set<Long> grantedMenuIds = ensureDefaultMenuGrants(tenantId, adminRoleId);
            AdminUserResult adminUser = userBootstrapFacade.ensureAdminUser(new EnsureAdminUserRequest(
                    tenantId,
                    rootDeptId,
                    normalizeAdminUsername(tenantId),
                    StringUtils.hasText(tenantName) ? tenantName.trim() + "管理员" : "租户管理员",
                    passwordHasher.hash(generateInitialPassword()),
                    1
            ));
            userBootstrapFacade.ensureUserRole(tenantId, adminUser.userId(), adminRoleId);
            return new BootstrapResult(rootDeptId, adminRoleId, adminUser.userId(), adminUser.username(), grantedMenuIds.size());
        });
    }

    private Set<Long> ensureDefaultMenuGrants(String tenantId, Long adminRoleId) {
        Set<Long> grantableMenuIds = flattenMenuIds(menuService.grantableTree(tenantId));
        if (grantableMenuIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> expandedMenuIds = menuService.expandMenuIdsWithAncestors(tenantId, grantableMenuIds);
        roleBootstrapFacade.grantMenus(tenantId, adminRoleId, expandedMenuIds);
        return expandedMenuIds;
    }

    private String normalizeAdminUsername(String tenantId) {
        String normalizedTenantId = USERNAME_ALLOWED_CHARS.matcher(tenantId.trim().toLowerCase(Locale.ROOT)).replaceAll("_");
        if (normalizedTenantId.length() > 58) {
            normalizedTenantId = normalizedTenantId.substring(0, 58);
        }
        return normalizedTenantId + "_admin";
    }

    private Set<Long> flattenMenuIds(Collection<MenuTreeNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Set.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        List<MenuTreeNode> queue = new ArrayList<>(nodes);
        for (int i = 0; i < queue.size(); i++) {
            MenuTreeNode node = queue.get(i);
            if (node.id() != null) {
                ids.add(node.id());
            }
            if (node.children() != null && !node.children().isEmpty()) {
                queue.addAll(node.children());
            }
        }
        return ids;
    }

    private <T> T withTenant(String tenantId, Supplier<T> supplier) {
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

    private String generateInitialPassword() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        log.info("Tenant bootstrap generated initial password (length={}) for tenant admin", password.length());
        return password;
    }

    public record BootstrapResult(
            Long rootDeptId,
            Long adminRoleId,
            Long adminUserId,
            String adminUsername,
            int grantedMenuCount
    ) {
        static BootstrapResult empty() {
            return new BootstrapResult(null, null, null, null, 0);
        }
    }
}