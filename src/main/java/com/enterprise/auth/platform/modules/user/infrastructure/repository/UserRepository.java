package com.enterprise.auth.platform.modules.user.infrastructure.repository;

import com.enterprise.auth.platform.modules.user.application.AuthenticationUser;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<AuthenticationUser> findByUsername(String tenantId, String username);

    Optional<AuthenticationUser> findById(Long id);

    List<AuthenticationUser> findAll();

    void incrementSessionVersion(Long userId);
}

