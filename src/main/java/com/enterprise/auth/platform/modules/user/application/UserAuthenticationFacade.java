package com.enterprise.auth.platform.modules.user.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserAuthenticationFacade {

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
        if (raw instanceof java.util.Map<?, ?> map) {
            try {
                return Optional.of(objectMapper.convertValue(map, AuthenticationUser.class));
            } catch (Exception ignored) {
            }
        }
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
        entity.setLastLoginAt(TimeSupport.utcNowDateTime());
        entity.setLastLoginIp(clientIp);
        entity.setUpdatedBy(entity.getUsername());
        sysUserMapper.updateById(entity);
    }

}