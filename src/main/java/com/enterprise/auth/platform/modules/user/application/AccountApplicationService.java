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
import com.enterprise.auth.platform.modules.file.application.FileApplicationService;
import com.enterprise.auth.platform.modules.file.application.FileMetadataView;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.interfaces.AccountPasswordChangeRequest;
import com.enterprise.auth.platform.modules.user.interfaces.AccountProfileResponse;
import com.enterprise.auth.platform.modules.user.interfaces.AccountProfileUpdateRequest;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AccountApplicationService {

    private final CurrentUserService currentUserService;
    private final SysUserMapper sysUserMapper;
    private final PasswordHasher passwordHasher;
    private final AuthPrincipalCacheService authPrincipalCacheService;
    private final FileApplicationService fileApplicationService;
    private final AuditService auditService;

    public AccountApplicationService(
            CurrentUserService currentUserService,
            SysUserMapper sysUserMapper,
            PasswordHasher passwordHasher,
            AuthPrincipalCacheService authPrincipalCacheService,
            FileApplicationService fileApplicationService,
            AuditService auditService
    ) {
        this.currentUserService = currentUserService;
        this.sysUserMapper = sysUserMapper;
        this.passwordHasher = passwordHasher;
        this.authPrincipalCacheService = authPrincipalCacheService;
        this.fileApplicationService = fileApplicationService;
        this.auditService = auditService;
    }

    public AccountProfileResponse profile() {
        UserAccount current = currentUserService.requireCurrentUser();
        return runWithAccountTenant(current, () -> {
            SysUserEntity user = loadCurrentUser(current);
            return toProfile(user);
        });
    }

    @Transactional
    public AccountProfileResponse updateProfile(AccountProfileUpdateRequest request) {
        UserAccount current = currentUserService.requireCurrentUser();
        return runWithAccountTenant(current, () -> {
            SysUserEntity user = loadCurrentUser(current);
            user.setDisplayName(normalizeProfileText(request.displayName(), 32));
            user.setMobile(normalizeProfileText(request.mobile(), 11));
            user.setEmail(normalizeProfileText(request.email(), 128));
            user.setUpdatedBy(user.getUsername());
            sysUserMapper.updateById(user);
            authPrincipalCacheService.evictByUser(user.getId(), user.getTenantId(), user.getUsername());
            auditService.record("PROFILE_UPDATED", user.getUsername(), user.getTenantId(), Map.of("userId", user.getId()));
            return toProfile(user);
        });
    }

    @Transactional
    public AccountProfileResponse updateAvatar(MultipartFile file) {
        UserAccount current = currentUserService.requireCurrentUser();
        return runWithAccountTenant(current, () -> {
            SysUserEntity user = loadCurrentUser(current);
            FileMetadataView uploaded = fileApplicationService.uploadCurrentUserAvatar(file);
            user.setAvatarFileKey(uploaded.fileKey());
            user.setUpdatedBy(user.getUsername());
            sysUserMapper.updateById(user);
            authPrincipalCacheService.evictByUser(user.getId(), user.getTenantId(), user.getUsername());
            auditService.record("AVATAR_UPDATED", user.getUsername(), user.getTenantId(), Map.of(
                    "userId", user.getId(),
                    "fileKey", uploaded.fileKey()
            ));
            return toProfile(user);
        });
    }

    @Transactional
    public AccountProfileResponse changePassword(AccountPasswordChangeRequest request) {
        UserAccount current = currentUserService.requireCurrentUser();
        return runWithAccountTenant(current, () -> {
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
            return toProfile(user);
        });
    }

    private AccountProfileResponse toProfile(SysUserEntity user) {
        String avatarFileKey = user.getAvatarFileKey();
        return new AccountProfileResponse(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getMobile(),
                user.getEmail(),
                avatarFileKey,
                fileApplicationService.publicUrl(avatarFileKey),
                user.getEnabled() == null || user.getEnabled() == 1,
                user.getMustChangePassword() != null && user.getMustChangePassword() == 1,
                user.getPasswordUpdatedAt(),
                user.getLastLoginAt(),
                user.getLastLoginIp(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private String normalizeProfileText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessException("VALIDATION_ERROR", "资料字段长度超出限制");
        }
        return trimmed;
    }

    private SysUserEntity loadCurrentUser(UserAccount current) {
        SysUserEntity user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getId, current.id())
                .eq(SysUserEntity::getTenantId, current.tenantId())
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }

    private <T> T runWithAccountTenant(UserAccount current, Supplier<T> supplier) {
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(current.tenantId());
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }
}