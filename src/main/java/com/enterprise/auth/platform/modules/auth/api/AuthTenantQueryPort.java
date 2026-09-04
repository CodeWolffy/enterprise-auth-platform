package com.enterprise.auth.platform.modules.auth.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Auth-owned contract for login and tenant-switch tenant data. */
public interface AuthTenantQueryPort {

    void ensureTenantAccessible(String tenantId);

    Optional<TenantSummary> findByTenantId(String tenantId);

    List<TenantSummary> listTenantRecords();

    record TenantSummary(
            String tenantId,
            String tenantName,
            Integer platformLevel,
            Integer tenantStatus,
            Instant authBeginAt,
            Instant expireAt
    ) {
    }
}
