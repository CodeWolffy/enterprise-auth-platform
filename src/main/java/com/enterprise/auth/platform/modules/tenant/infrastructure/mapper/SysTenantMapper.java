package com.enterprise.auth.platform.modules.tenant.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenantEntity> {
}

