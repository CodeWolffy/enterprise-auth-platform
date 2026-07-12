package com.enterprise.auth.platform.modules.auth.application;

import java.util.List;

/** 数据权限用部门查询端口，由 dept 模块实现。 */
public interface DataScopeDeptQuery {

    List<ScopedDept> listActive(String tenantId);

    record ScopedDept(Long id, Long parentId) {
    }
}