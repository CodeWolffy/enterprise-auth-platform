package com.enterprise.auth.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.persistence.entity.SysRoleEntity;
import com.enterprise.auth.platform.persistence.entity.SysRolePermissionEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.persistence.mapper.SysRoleMapper;
import com.enterprise.auth.platform.persistence.mapper.SysRolePermissionMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.role.dto.CreateRoleRequest;
import com.enterprise.auth.platform.role.service.RoleManagementService;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(properties = "spring.cache.type=simple")
class AuthPrincipalCacheInvalidationIntegrationTest {

    @Autowired
    private RoleManagementService roleManagementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private SysRolePermissionMapper sysRolePermissionMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tenantId;
    private String username;
    private String roleCode;
    private Long userId;
    private Long roleId;

    @BeforeEach
    void setUp() {
        tenantId = "platform";
        username = "cache_user_" + System.nanoTime();
        roleCode = "CACHE_ROLE_" + System.nanoTime();
        TenantContext.setTenantId(tenantId);

        SysUserEntity user = new SysUserEntity();
        user.setTenantId(tenantId);
        user.setDeptId(1L);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setPasswordHash(passwordEncoder.encode("CacheTest@123"));
        user.setEnabled(1);
        user.setSessionVersion(1);
        sysUserMapper.insert(user);
        userId = user.getId();

        roleManagementService.create(new CreateRoleRequest(
                roleCode,
                roleCode,
                "cache invalidation test role",
                DataScopeType.SELF,
                List.of()
        ));
        SysRoleEntity role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getRoleCode, roleCode)
                .eq(SysRoleEntity::getDeleted, 0)
                .last("limit 1"));
        roleId = role.getId();

        SysUserRoleEntity userRole = new SysUserRoleEntity();
        userRole.setTenantId(tenantId);
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        sysUserRoleMapper.insert(userRole);

        Cache principalCache = cacheManager.getCache(AuthPrincipalCacheService.CACHE_NAME);
        if (principalCache != null) {
            principalCache.clear();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (userId != null) {
            sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRoleEntity>()
                    .eq(SysUserRoleEntity::getTenantId, tenantId)
                    .eq(SysUserRoleEntity::getUserId, userId));
            sysUserMapper.delete(new LambdaQueryWrapper<SysUserEntity>()
                    .eq(SysUserEntity::getTenantId, tenantId)
                    .eq(SysUserEntity::getId, userId));
        }
        if (roleId != null) {
            sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermissionEntity>()
                    .eq(SysRolePermissionEntity::getTenantId, tenantId)
                    .eq(SysRolePermissionEntity::getRoleId, roleId));
            sysRoleMapper.delete(new LambdaQueryWrapper<SysRoleEntity>()
                    .eq(SysRoleEntity::getTenantId, tenantId)
                    .eq(SysRoleEntity::getId, roleId));
        }
        Cache principalCache = cacheManager.getCache(AuthPrincipalCacheService.CACHE_NAME);
        if (principalCache != null) {
            principalCache.clear();
        }
    }

    @Test
    void shouldEvictUserPrincipalCacheWhenRolePermissionsChanged() {
        List<CatalogService.PermissionView> permissions = catalogService.permissions();
        assertThat(permissions).hasSizeGreaterThanOrEqualTo(2);
        String firstPermissionCode = permissions.get(0).permissionCode();
        String secondPermissionCode = permissions.get(1).permissionCode();

        roleManagementService.assignPermissions(roleId, Set.of(firstPermissionCode));

        UserAccount cachedById = userRepository.findById(userId).orElseThrow();
        UserAccount cachedByUsername = userRepository.findByUsername(tenantId, username).orElseThrow();
        assertThat(cachedById.permissions()).contains(firstPermissionCode);
        assertThat(cachedByUsername.permissions()).contains(firstPermissionCode);

        Cache principalCache = cacheManager.getCache(AuthPrincipalCacheService.CACHE_NAME);
        assertThat(principalCache).isNotNull();
        assertThat(principalCache.get(AuthPrincipalCacheService.idKey(userId))).isNotNull();
        assertThat(principalCache.get(AuthPrincipalCacheService.usernameKey(tenantId, username))).isNotNull();

        roleManagementService.assignPermissions(roleId, Set.of(secondPermissionCode));

        assertThat(principalCache.get(AuthPrincipalCacheService.idKey(userId))).isNull();
        assertThat(principalCache.get(AuthPrincipalCacheService.usernameKey(tenantId, username))).isNull();

        UserAccount refreshed = userRepository.findById(userId).orElseThrow();
        assertThat(refreshed.permissions())
                .contains(secondPermissionCode)
                .doesNotContain(firstPermissionCode);
    }
}
