package com.enterprise.auth.platform.modules.iam.api;

import java.util.List;

/** IAM contract for user references required by role management. */
public interface IamRoleUserReferencePort {

    List<Long> listUserIdsByRole(String tenantId, Long roleId);

    long countUsersByRole(String tenantId, Long roleId);

    List<RoleUser> findByIds(List<Long> userIds);

    record RoleUser(Long id, String tenantId, String username) {
    }
}
