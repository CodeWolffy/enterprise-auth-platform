package com.enterprise.auth.platform.modules.iam.api;

import java.util.List;

/** IAM data-scope department query implemented by the department module. */
public interface DataScopeDeptQuery {

    List<ScopedDept> listActive(String tenantId);

    record ScopedDept(Long id, Long parentId) {
    }
}
