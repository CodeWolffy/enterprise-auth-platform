package com.enterprise.auth.platform.modules.security.infrastructure.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.security.infrastructure.entity.SysPlatformSecurityPolicyEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface SysPlatformSecurityPolicyMapper extends BaseMapper<SysPlatformSecurityPolicyEntity> {
}