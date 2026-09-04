package com.enterprise.auth.platform.modules.iam.api;

/** IAM contract for user references required by department management. */
public interface IamDeptUserReferencePort {

    long countByDept(String tenantId, Long deptId);

    boolean userExists(String tenantId, Long userId);
}
