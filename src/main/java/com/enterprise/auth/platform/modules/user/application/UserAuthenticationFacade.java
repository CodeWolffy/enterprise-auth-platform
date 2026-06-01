package com.enterprise.auth.platform.modules.user.application;

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

    public UserAuthenticationFacade(
            UserRepository userRepository,
            SysUserMapper sysUserMapper
    ) {
        this.userRepository = userRepository;
        this.sysUserMapper = sysUserMapper;
    }

    public Optional<AuthenticationUser> findByUsername(String tenantId, String username) {
        return userRepository.findByUsername(tenantId, username);
    }

    public Optional<AuthenticationUser> findById(Long id) {
        return userRepository.findById(id);
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