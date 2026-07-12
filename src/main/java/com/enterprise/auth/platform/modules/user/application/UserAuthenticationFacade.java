package com.enterprise.auth.platform.modules.user.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserAuthenticationFacade {

    private static final Logger log = LoggerFactory.getLogger(UserAuthenticationFacade.class);

    private final UserRepository userRepository;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    public UserAuthenticationFacade(
            UserRepository userRepository,
            SysUserMapper sysUserMapper,
            ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
    }

    public Optional<AuthenticationUser> findByUsername(String tenantId, String username) {
        Optional<AuthenticationUser> result = userRepository.findByUsername(tenantId, username);
        return normalizeUser(result);
    }

    public Optional<AuthenticationUser> findById(Long id) {
        Optional<AuthenticationUser> result = userRepository.findById(id);
        return normalizeUser(result);
    }

    @SuppressWarnings("unchecked")
    private Optional<AuthenticationUser> normalizeUser(Optional<AuthenticationUser> result) {
        if (result.isEmpty()) {
            return Optional.empty();
        }
        Object raw = result.get();
        if (raw instanceof AuthenticationUser user) {
            return Optional.of(user);
        }
        // 旧 Map 缓存兼容已移除：namespace v7 淘汰旧格式后仅接受 AuthenticationUser
        log.debug("缓存用户类型非 AuthenticationUser，已丢弃。type={}", raw.getClass().getName());
        return Optional.empty();
    }

    public List<String> activeTenantIdsByUsername(String username) {
        return sysUserMapper.selectActiveTenantIdsByUsername(username).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    public void recordLoginSuccess(Long userId, String clientIp) {
        if (userId == null) {
            return;
        }
        SysUserEntity entity = sysUserMapper.selectById(userId);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            return;
        }
        entity.setLastLoginAt(TimeSupport.now());
        entity.setLastLoginIp(clientIp);
        entity.setUpdatedBy(entity.getUsername());
        sysUserMapper.updateById(entity);
    }

    public Optional<SysUserEntity> findActiveEntity(String tenantId, String username) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(username)) {
            return Optional.empty();
        }
        return withTenant(tenantId, () -> Optional.ofNullable(sysUserMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getUsername, username)
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"))));
    }

    public Optional<SysUserEntity> findActiveEntityById(String tenantId, Long userId) {
        if (!StringUtils.hasText(tenantId) || userId == null) {
            return Optional.empty();
        }
        return withTenant(tenantId, () -> {
            SysUserEntity entity = sysUserMapper.selectById(userId);
            if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
                return Optional.empty();
            }
            return Optional.of(entity);
        });
    }

    public void update(SysUserEntity entity) {
        if (entity == null || !StringUtils.hasText(entity.getTenantId())) {
            return;
        }
        withTenant(entity.getTenantId(), () -> {
            sysUserMapper.updateById(entity);
            return null;
        });
    }

    public <T> T withTenant(String tenantId, Supplier<T> action) {
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            return action.get();
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

}