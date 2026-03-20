package com.enterprise.auth.platform.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysOauthClientMapper extends BaseMapper<SysOauthClientEntity> {

    @Select("""
            SELECT id, tenant_id, client_id, client_secret, client_name, redirect_uris, scopes, grant_types,
                   require_pkce, require_consent, created_by, updated_by, deleted, created_at, updated_at
            FROM sys_oauth_client
            WHERE tenant_id = #{tenantId}
              AND client_id = #{clientId}
            LIMIT 1
            """)
    SysOauthClientEntity selectIncludingDeleted(
            @Param("tenantId") String tenantId,
            @Param("clientId") String clientId
    );

    @Delete("""
            DELETE FROM sys_oauth_client
            WHERE tenant_id = #{tenantId}
              AND client_id = #{clientId}
            """)
    int hardDeleteByTenantAndClientId(
            @Param("tenantId") String tenantId,
            @Param("clientId") String clientId
    );
}
