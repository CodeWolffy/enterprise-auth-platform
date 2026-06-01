package com.enterprise.auth.platform.modules.user.infrastructure.repository;

import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<UserAccount> findByUsername(String tenantId, String username);

    Optional<UserAccount> findById(Long id);

    List<UserAccount> findAll();

    void incrementSessionVersion(Long userId);
}

