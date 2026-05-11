package com.enterprise.auth.platform.model.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.model.entity.SysUserEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserEntity> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COUNT(1)
            FROM sys_user
            WHERE username = #{username}
              AND deleted = 0
            """)
    long countActiveByUsername(@Param("username") String username);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT tenant_id
            FROM sys_user
            WHERE username = #{username}
              AND deleted = 0
            ORDER BY tenant_id
            """)
    List<String> selectActiveTenantIdsByUsername(@Param("username") String username);
}
