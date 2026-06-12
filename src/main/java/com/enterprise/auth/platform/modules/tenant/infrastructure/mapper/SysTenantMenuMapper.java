package com.enterprise.auth.platform.modules.tenant.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantMenuEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysTenantMenuMapper extends BaseMapper<SysTenantMenuEntity> {
}