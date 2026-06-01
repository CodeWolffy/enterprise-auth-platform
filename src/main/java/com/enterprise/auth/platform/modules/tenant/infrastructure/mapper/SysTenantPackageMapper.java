package com.enterprise.auth.platform.modules.tenant.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantPackageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysTenantPackageMapper extends BaseMapper<SysTenantPackageEntity> {
}
