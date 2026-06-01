package com.enterprise.auth.platform.modules.resource.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.resource.infrastructure.entity.SysResourceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysResourceMapper extends BaseMapper<SysResourceEntity> {
}
