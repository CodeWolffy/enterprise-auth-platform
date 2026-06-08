package com.enterprise.auth.platform.modules.role.infrastructure.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleMenuEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenuEntity> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(1) FROM sys_role_menu WHERE menu_id = #{menuId}")
    long countByMenuIdAcrossTenants(@Param("menuId") Long menuId);

    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_role_menu WHERE menu_id = #{menuId}")
    int deleteByMenuIdAcrossTenants(@Param("menuId") Long menuId);
}