package com.enterprise.auth.platform.modules.tenant.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantMenuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface SysTenantMenuMapper extends BaseMapper<SysTenantMenuEntity> {

    @Delete("""
            DELETE rm
            FROM sys_role_menu rm
            LEFT JOIN sys_tenant_menu tm
              ON tm.tenant_id = rm.tenant_id
             AND tm.menu_id = rm.menu_id
            WHERE rm.tenant_id = #{tenantId}
              AND tm.menu_id IS NULL
            """)
    int deleteRoleMenusOutsideTenantMenus(@Param("tenantId") String tenantId);
}
