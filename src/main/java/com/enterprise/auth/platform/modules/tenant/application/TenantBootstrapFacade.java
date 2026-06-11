package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import com.enterprise.auth.platform.modules.dept.infrastructure.entity.SysDeptEntity;
import com.enterprise.auth.platform.modules.dept.infrastructure.mapper.SysDeptMapper;
import com.enterprise.auth.platform.modules.menu.application.MenuService;
import com.enterprise.auth.platform.modules.menu.domain.MenuTreeNode;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleMenuEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMenuMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserRoleMapper;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    public static final String TENANT_ADMIN_ROLE_CODE = "TENANT_ADMIN";
    private static final String ROOT_DEPT_CODE = "ROOT";
    private static final Pattern USERNAME_ALLOWED_CHARS = Pattern.compile("[^a-zA-Z0-9_.-]");

    private final SysDeptMapper sysDeptMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordHasher passwordHasher;
    private final MenuService menuService;
    private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();

    public TenantBootstrapFacade(
            SysDeptMapper sysDeptMapper,
            SysRoleMapper sysRoleMapper,
            SysRoleMenuMapper sysRoleMenuMapper,
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            PasswordHasher passwordHasher,
            MenuService menuService
    ) {
        this.sysDeptMapper = sysDeptMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordHasher = passwordHasher;
        this.menuService = menuService;
    }

    public BootstrapResult bootstrap(String tenantId, String tenantName, String operator) {
        if (!StringUtils.hasText(tenantId)) {
            return BootstrapResult.empty();
        }
        return withTenant(tenantId, () -> {
            SysDeptEntity rootDept = ensureRootDept(tenantId, tenantName);
            SysRoleEntity adminRole = ensureTenantAdminRole(tenantId);
            Set<Long> grantedMenuIds = ensureDefaultMenuGrants(tenantId, adminRole);
            SysUserEntity adminUser = ensureTenantAdminUser(tenantId, tenantName, rootDept.getId());
            ensureUserRole(tenantId, adminUser.getId(), adminRole.getId());
            return new BootstrapResult(rootDept.getId(), adminRole.getId(), adminUser.getId(), adminUser.getUsername(), grantedMenuIds.size());
        });
    }

    private SysDeptEntity ensureRootDept(String tenantId, String tenantName) {
        SysDeptEntity existing = sysDeptMapper.selectOne(new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getTenantId, tenantId)
                .eq(SysDeptEntity::getDeptCode, ROOT_DEPT_CODE)
                .eq(SysDeptEntity::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        SysDeptEntity entity = new SysDeptEntity();
        entity.setTenantId(tenantId);
        entity.setParentId(null);
        entity.setDeptCode(ROOT_DEPT_CODE);
        entity.setDeptName(StringUtils.hasText(tenantName) ? tenantName.trim() : "根部门");
        entity.setLeaderUserId(null);
        entity.setLeaderName(null);
        entity.setLeaderPhone(null);
        entity.setOrderNo(0);
        entity.setEnabled(1);
        sysDeptMapper.insert(entity);
        return entity;
    }

    private SysRoleEntity ensureTenantAdminRole(String tenantId) {
        SysRoleEntity existing = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getRoleCode, TENANT_ADMIN_ROLE_CODE)
                .eq(SysRoleEntity::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        SysRoleEntity entity = new SysRoleEntity();
        entity.setTenantId(tenantId);
        entity.setRoleCode(TENANT_ADMIN_ROLE_CODE);
        entity.setRoleName("租户管理员");
        entity.setRoleDesc("租户初始化自动创建的管理员角色");
        entity.setDataScopeType(DataScopeType.ALL.name());
        entity.setDataScopeValueJson(null);
        sysRoleMapper.insert(entity);
        return entity;
    }

    private Set<Long> ensureDefaultMenuGrants(String tenantId, SysRoleEntity adminRole) {
        Set<Long> grantableMenuIds = flattenMenuIds(menuService.grantableTree(tenantId));
        if (grantableMenuIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> expandedMenuIds = menuService.expandMenuIdsWithAncestors(tenantId, grantableMenuIds);
        for (Long menuId : expandedMenuIds) {
            Long existing = sysRoleMenuMapper.selectCount(new LambdaQueryWrapper<SysRoleMenuEntity>()
                    .eq(SysRoleMenuEntity::getTenantId, tenantId)
                    .eq(SysRoleMenuEntity::getRoleId, adminRole.getId())
                    .eq(SysRoleMenuEntity::getMenuId, menuId));
            if (Optional.ofNullable(existing).orElse(0L) > 0) {
                continue;
            }
            SysRoleMenuEntity relation = new SysRoleMenuEntity();
            relation.setTenantId(tenantId);
            relation.setRoleId(adminRole.getId());
            relation.setMenuId(menuId);
            sysRoleMenuMapper.insert(relation);
        }
        return expandedMenuIds;
    }

    private SysUserEntity ensureTenantAdminUser(String tenantId, String tenantName, Long rootDeptId) {
        String preferredUsername = normalizeAdminUsername(tenantId);
        SysUserEntity existing = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getUsername, preferredUsername)
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        String username = nextAvailableUsername(preferredUsername);
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId(tenantId);
        entity.setDeptId(rootDeptId);
        entity.setUsername(username);
        entity.setDisplayName(StringUtils.hasText(tenantName) ? tenantName.trim() + "管理员" : "租户管理员");
        entity.setMobile(null);
        entity.setEmail(null);
        entity.setAvatarFileKey(null);
        entity.setPasswordHash(passwordHasher.hash(generateInitialPassword()));
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        entity.setMustChangePassword(1);
        entity.setPasswordUpdatedAt(TimeSupport.utcNowDateTime());
        sysUserMapper.insert(entity);
        return entity;
    }

    private void ensureUserRole(String tenantId, Long userId, Long roleId) {
        Long existing = sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .eq(SysUserRoleEntity::getUserId, userId)
                .eq(SysUserRoleEntity::getRoleId, roleId));
        if (Optional.ofNullable(existing).orElse(0L) > 0) {
            return;
        }
        SysUserRoleEntity relation = new SysUserRoleEntity();
        relation.setTenantId(tenantId);
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        sysUserRoleMapper.insert(relation);
    }

    private String normalizeAdminUsername(String tenantId) {
        String normalizedTenantId = USERNAME_ALLOWED_CHARS.matcher(tenantId.trim().toLowerCase(Locale.ROOT)).replaceAll("_");
        if (normalizedTenantId.length() > 58) {
            normalizedTenantId = normalizedTenantId.substring(0, 58);
        }
        return normalizedTenantId + "_admin";
    }

    private String nextAvailableUsername(String preferredUsername) {
        if (sysUserMapper.countActiveByUsername(preferredUsername) == 0) {
            return preferredUsername;
        }
        for (int i = 1; i <= 100; i++) {
            String candidate = preferredUsername + "_" + i;
            if (sysUserMapper.countActiveByUsername(candidate) == 0) {
                return candidate;
            }
        }
        return preferredUsername + "_" + System.currentTimeMillis();
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