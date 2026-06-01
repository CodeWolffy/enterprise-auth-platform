package com.enterprise.auth.platform.modules.system.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysConfigEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfigEntity> {
}
