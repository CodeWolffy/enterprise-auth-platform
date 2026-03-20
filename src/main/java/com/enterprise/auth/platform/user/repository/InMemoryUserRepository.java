package com.enterprise.auth.platform.user.repository;

import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.user.model.UserAccount;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryUserRepository implements UserRepository {

    private final PasswordEncoder passwordEncoder;
    private final Map<Long, UserAccount> users = new ConcurrentHashMap<>();

    public InMemoryUserRepository(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    void init() {
        // 这里的种子账号仅用于首期骨架演示，后续会由 MySQL/Redis 持久化实现替换。
        save(new UserAccount(
                1L,
                "platform",
                "admin",
                passwordEncoder.encode("Admin@123456"),
                true,
                Set.of("ADMIN"),
                Set.of(
                        "auth:read",
                        "auth:write",
                        "user:read",
                        "user:write",
                        "role:read",
                        "role:write",
                        "permission:read",
                        "permission:write",
                        "dept:read",
                        "dept:write",
                        "tenant:read",
                        "tenant:write",
                        "audit:read",
                        "audit:write",
                        "system:read",
                        "system:write",
                        "session:write"
                ),
                Set.of(),
                DataScopeType.ALL,
                1
        ));
        save(new UserAccount(
                2L,
                "tenant-a",
                "auditor",
                passwordEncoder.encode("Auditor@123456"),
                true,
                Set.of("AUDITOR"),
                Set.of("auth:read", "audit:read", "user:read", "permission:read"),
                Set.of(1001L),
                DataScopeType.DEPT,
                1
        ));
    }

    @Override
    public Optional<UserAccount> findByUsername(String tenantId, String username) {
        return users.values().stream()
                .filter(user -> user.tenantId().equals(tenantId) && user.username().equals(username))
                .findFirst();
    }

    @Override
    public Optional<UserAccount> findById(Long id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public List<UserAccount> findAll() {
        return users.values().stream().toList();
    }

    @Override
    public void incrementSessionVersion(Long userId) {
        users.computeIfPresent(userId, (id, user) -> new UserAccount(
                user.id(),
                user.tenantId(),
                user.username(),
                user.password(),
                user.enabled(),
                user.roles(),
                user.permissions(),
                user.customDeptIds(),
                user.dataScopeType(),
                user.sessionVersion() + 1
        ));
    }

    private void save(UserAccount account) {
        users.put(account.id(), account);
    }
}
