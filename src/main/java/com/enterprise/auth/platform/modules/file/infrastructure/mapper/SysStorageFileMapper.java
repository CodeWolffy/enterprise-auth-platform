package com.enterprise.auth.platform.modules.file.infrastructure.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.file.infrastructure.entity.SysStorageFileEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysStorageFileMapper extends BaseMapper<SysStorageFileEntity> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM sys_storage_file
            WHERE file_key = #{fileKey}
              AND deleted = 0
            LIMIT 1
            """)
    SysStorageFileEntity selectByFileKeyIgnoreTenant(@Param("fileKey") String fileKey);
}