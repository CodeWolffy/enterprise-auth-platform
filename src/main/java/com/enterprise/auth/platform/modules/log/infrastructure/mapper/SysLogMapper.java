package com.enterprise.auth.platform.modules.log.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysLogMapper extends BaseMapper<SysLogEntity> {
}