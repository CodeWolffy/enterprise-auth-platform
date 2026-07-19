package com.enterprise.auth.platform.modules.tenant.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenantEntity> {

    @Update("""
            UPDATE sys_tenant
               SET package_code = #{newPackageCode}
             WHERE deleted = 0
               AND package_code = #{oldPackageCode}
            """)
    int updatePackageCodeReferences(
            @Param("oldPackageCode") String oldPackageCode,
            @Param("newPackageCode") String newPackageCode
    );

    @Select("""
            SELECT tenant_id
              FROM sys_tenant
             WHERE deleted = 0
               AND package_code = #{packageCode}
             ORDER BY id
            """)
    List<String> selectTenantIdsByPackageCode(@Param("packageCode") String packageCode);
}

