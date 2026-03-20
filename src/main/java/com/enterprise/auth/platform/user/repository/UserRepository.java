package com.enterprise.auth.platform.user.repository;

import com.enterprise.auth.platform.user.model.UserAccount;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<UserAccount> findByUsername(String tenantId, String username);

    Optional<UserAccount> findById(Long id);

    List<UserAccount> findAll();

    void incrementSessionVersion(Long userId);
}

