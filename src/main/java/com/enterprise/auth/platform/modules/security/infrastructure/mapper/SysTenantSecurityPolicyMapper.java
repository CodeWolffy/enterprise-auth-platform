package com.enterprise.auth.platform.modules.security.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.security.infrastructure.entity.SysTenantSecurityPolicyEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysTenantSecurityPolicyMapper extends BaseMapper<SysTenantSecurityPolicyEntity> {
}