package com.enterprise.auth.platform.modules.iam.api;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** IAM role query contract implemented by the role module. */
public interface IamRoleQueryPort {

    RoleAuthorization resolveAuthorization(String tenantId, Set<Long> roleIds);

    Map<Long, String> loadRoleCodeMap(String tenantId);

    Set<String> resolveGrantKeys(String tenantId, Set<String> roleCodes, boolean superAdmin);

    Map<String, Long> loadRoleIdMap(String tenantId, Set<String> roleCodes);

    List<RoleSummary> listRolesByIds(String tenantId, Set<Long> roleIds);

    record RoleAuthorization(
            Set<String> roleCodes,
            Set<String> permissionCodes,
            Set<Long> customDeptIds,
            DataScopeType dataScopeType
    ) {
        public RoleAuthorization {
            roleCodes = roleCodes == null ? Set.of() : Set.copyOf(roleCodes);
            permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
            customDeptIds = customDeptIds == null ? Set.of() : Set.copyOf(customDeptIds);
            dataScopeType = dataScopeType == null ? DataScopeType.SELF : dataScopeType;
        }
    }

    record RoleSummary(
            Long id,
            String tenantId,
            String code,
            String name,
            String description,
            DataScopeType dataScopeType,
            List<Long> customDeptIds
    ) {
        public RoleSummary {
            customDeptIds = customDeptIds == null ? List.of() : List.copyOf(customDeptIds);
            dataScopeType = dataScopeType == null ? DataScopeType.SELF : dataScopeType;
        }
    }
}
