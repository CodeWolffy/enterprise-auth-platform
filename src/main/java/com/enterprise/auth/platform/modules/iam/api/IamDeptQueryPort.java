package com.enterprise.auth.platform.modules.iam.api;

import java.util.List;

/** IAM department query contract implemented by the department module. */
public interface IamDeptQueryPort {

    long countByIds(String tenantId, List<Long> deptIds);
}
