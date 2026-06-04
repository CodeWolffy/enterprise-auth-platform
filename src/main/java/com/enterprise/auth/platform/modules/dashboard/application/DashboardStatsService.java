package com.enterprise.auth.platform.modules.dashboard.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.audit.infrastructure.entity.SysAuditLogEntity;
import com.enterprise.auth.platform.modules.audit.infrastructure.mapper.SysAuditLogMapper;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse;
import com.enterprise.auth.platform.modules.file.infrastructure.entity.SysStorageFileEntity;
import com.enterprise.auth.platform.modules.file.infrastructure.mapper.SysStorageFileMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DashboardStatsService {

    private final CurrentUserService currentUserService;
    private final PlatformAdminSupport platformAdminSupport;
    private final DataScopeService dataScopeService;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysTenantMapper sysTenantMapper;
    private final SysStorageFileMapper sysStorageFileMapper;
    private final SysAuditLogMapper sysAuditLogMapper;

    public DashboardStatsService(
            CurrentUserService currentUserService,
            PlatformAdminSupport platformAdminSupport,
            DataScopeService dataScopeService,
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysTenantMapper sysTenantMapper,
            SysStorageFileMapper sysStorageFileMapper,
            SysAuditLogMapper sysAuditLogMapper
    ) {
        this.currentUserService = currentUserService;
        this.platformAdminSupport = platformAdminSupport;
        this.dataScopeService = dataScopeService;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysTenantMapper = sysTenantMapper;
        this.sysStorageFileMapper = sysStorageFileMapper;
        this.sysAuditLogMapper = sysAuditLogMapper;
    }

    public DashboardStatsResponse stats() {
        UserAccount user = currentUserService.requireCurrentUser();
        String activeTenantId = activeTenantId(user);
        boolean platformScope = platformAdminSupport.isPlatformSuperAdmin(user) && "platform".equals(activeTenantId);
        String scope = platformScope ? "PLATFORM" : user.dataScopeType().name().equals("ALL") ? "TENANT" : "VISIBLE";
        Optional<Set<Long>> visibleUserIds = platformScope ? Optional.empty() : dataScopeService.visibleUserIds(activeTenantId);
        Optional<Set<String>> visibleUsernames = platformScope ? Optional.empty() : dataScopeService.visibleUsernames(activeTenantId);

        long userCount = countUsers(activeTenantId, platformScope, visibleUserIds);
        long roleCount = countRoles(activeTenantId, platformScope);
        long tenantCount = platformScope ? countTenants() : 1;
        long fileCount = countFiles(activeTenantId, platformScope, visibleUserIds);
        long storageBytes = sumStorageBytes(activeTenantId, platformScope, visibleUserIds);
        long operationLogCount = countOperationLogs(activeTenantId, platformScope, visibleUsernames, false);
        long recentOperationLogCount = countOperationLogs(activeTenantId, platformScope, visibleUsernames, true);

        return new DashboardStatsResponse(
                scope,
                platformScope ? null : activeTenantId,
                userCount,
                roleCount,
                tenantCount,
                fileCount,
                storageBytes,
                operationLogCount,
                recentOperationLogCount
        );
    }

    private long countUsers(String tenantId, boolean platformScope, Optional<Set<Long>> visibleUserIds) {
        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getDeleted, 0);
        if (!platformScope) {
            wrapper.eq(SysUserEntity::getTenantId, tenantId);
            applyVisibleUserIds(wrapper, visibleUserIds, SysUserEntity::getId);
        }
        return sysUserMapper.selectCount(wrapper);
    }

    private long countRoles(String tenantId, boolean platformScope) {
        LambdaQueryWrapper<SysRoleEntity> wrapper = new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getDeleted, 0);
        if (!platformScope) {
            wrapper.eq(SysRoleEntity::getTenantId, tenantId);
        }
        return sysRoleMapper.selectCount(wrapper);
    }

    private long countTenants() {
        return sysTenantMapper.selectCount(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0));
    }

    private long countFiles(String tenantId, boolean platformScope, Optional<Set<Long>> visibleUserIds) {
        LambdaQueryWrapper<SysStorageFileEntity> wrapper = fileScope(tenantId, platformScope, visibleUserIds);
        return sysStorageFileMapper.selectCount(wrapper);
    }

    private long sumStorageBytes(String tenantId, boolean platformScope, Optional<Set<Long>> visibleUserIds) {
        LambdaQueryWrapper<SysStorageFileEntity> wrapper = fileScope(tenantId, platformScope, visibleUserIds)
                .select(SysStorageFileEntity::getFileSize);
        return sysStorageFileMapper.selectList(wrapper).stream()
                .map(SysStorageFileEntity::getFileSize)
                .filter(size -> size != null && size > 0)
                .mapToLong(Long::longValue)
                .sum();
    }

    private LambdaQueryWrapper<SysStorageFileEntity> fileScope(String tenantId, boolean platformScope, Optional<Set<Long>> visibleUserIds) {
        LambdaQueryWrapper<SysStorageFileEntity> wrapper = new LambdaQueryWrapper<SysStorageFileEntity>()
                .eq(SysStorageFileEntity::getDeleted, 0);
        if (!platformScope) {
            wrapper.eq(SysStorageFileEntity::getTenantId, tenantId);
            applyVisibleUserIds(wrapper, visibleUserIds, SysStorageFileEntity::getOwnerUserId);
        }
        return wrapper;
    }

    private long countOperationLogs(
            String tenantId,
            boolean platformScope,
            Optional<Set<String>> visibleUsernames,
            boolean recentOnly
    ) {
        LambdaQueryWrapper<SysAuditLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (!platformScope) {
            wrapper.eq(SysAuditLogEntity::getTenantId, tenantId);
            visibleUsernames.ifPresent(usernames -> {
                if (usernames.isEmpty()) {
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.in(SysAuditLogEntity::getOperator, usernames);
                }
            });
        }
        if (recentOnly) {
            wrapper.ge(SysAuditLogEntity::getOccurredAt, TimeSupport.utcNowDateTime().minusDays(1));
        }
        return sysAuditLogMapper.selectCount(wrapper);
    }

    private <T> void applyVisibleUserIds(
            LambdaQueryWrapper<T> wrapper,
            Optional<Set<Long>> visibleUserIds,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column
    ) {
        visibleUserIds.ifPresent(userIds -> {
            if (userIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(column, userIds);
            }
        });
    }

    private String activeTenantId(UserAccount user) {
        String tenantId = TenantContext.getTenantId();
        if (StringUtils.hasText(tenantId)) {
            return tenantId;
        }
        return StringUtils.hasText(user.tenantId()) ? user.tenantId() : "platform";
    }
}