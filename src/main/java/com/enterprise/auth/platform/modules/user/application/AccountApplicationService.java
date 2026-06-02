package com.enterprise.auth.platform.modules.user.application;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.PasswordValidator;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.auth.infrastructure.AuthPrincipalCacheService;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.interfaces.AccountPasswordChangeRequest;
import com.enterprise.auth.platform.modules.user.interfaces.AccountProfileResponse;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountApplicationService {

    private final CurrentUserService currentUserService;
    private final SysUserMapper sysUserMapper;
    private final PasswordHasher passwordHasher;
    private final AuthPrincipalCacheService authPrincipalCacheService;
    private final AuditService auditService;

    public AccountApplicationService(
            CurrentUserService currentUserService,
            SysUserMapper sysUserMapper,
            PasswordHasher passwordHasher,
            AuthPrincipalCacheService authPrincipalCacheService,
            AuditService auditService
    ) {
        this.currentUserService = currentUserService;
        this.sysUserMapper = sysUserMapper;
        this.passwordHasher = passwordHasher;
        this.authPrincipalCacheService = authPrincipalCacheService;
        this.auditService = auditService;
    }

    public AccountProfileResponse profile() {
        UserAccount current = currentUserService.requireCurrentUser();
        SysUserEntity user = loadCurrentUser(current);
        return new AccountProfileResponse(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getMobile(),
                user.getEmail(),
                user.getMustChangePassword() != null && user.getMustChangePassword() == 1,
                user.getPasswordUpdatedAt()
        );
    }

    @Transactional
    public AccountProfileResponse changePassword(AccountPasswordChangeRequest request) {
        UserAccount current = currentUserService.requireCurrentUser();
        SysUserEntity user = loadCurrentUser(current);
        if (!passwordHasher.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException("BAD_CREDENTIALS", "原密码错误");
        }
        PasswordValidator.validate(request.newPassword());
        if (passwordHasher.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("PASSWORD_REUSED", "新密码不能与当前密码相同");
        }

        int nextSessionVersion = (user.getSessionVersion() == null ? 1 : user.getSessionVersion()) + 1;
        user.setPasswordHash(passwordHasher.hash(request.newPassword()));
        user.setSessionVersion(nextSessionVersion);
        user.setMustChangePassword(0);
        user.setPasswordUpdatedAt(TimeSupport.utcNowDateTime());
        user.setUpdatedBy(user.getUsername());
        sysUserMapper.updateById(user);

        StpUtil.getTokenSession().set("sessionVersion", nextSessionVersion);
        StpUtil.getTokenSession().set("passwordChangeRequired", false);
        StpUtil.getTokenSession().delete("passwordChangeReason");
        authPrincipalCacheService.evictByUser(user.getId(), user.getTenantId(), user.getUsername());
        auditService.record("PASSWORD_CHANGED", user.getUsername(), user.getTenantId(), Map.of("userId", user.getId()));
        return profile();
    }

    private SysUserEntity loadCurrentUser(UserAccount current) {
        SysUserEntity user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getId, current.id())
                .eq(SysUserEntity::getTenantId, TenantContext.getTenantId())
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }
}