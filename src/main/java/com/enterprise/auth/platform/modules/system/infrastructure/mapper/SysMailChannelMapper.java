package com.enterprise.auth.platform.modules.system.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysMailChannelEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysMailChannelMapper extends BaseMapper<SysMailChannelEntity> {

    @Delete("DELETE FROM sys_mail_channel WHERE tenant_id = #{tenantId}")
    int hardDeleteByTenantId(@Param("tenantId") String tenantId);
}