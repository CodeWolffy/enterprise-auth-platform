package com.enterprise.auth.platform.modules.system.api;

import java.util.Optional;
import java.util.Set;

/** System-owned contract for operator identity and creator-based data scope. */
public interface SystemAccessControlPort {

    String currentOperator();

    Optional<Set<String>> visibleUsernames(String tenantId);

    boolean canAccessCreatedBy(String tenantId, String createdBy);

    String currentScopeCacheKey();
}
