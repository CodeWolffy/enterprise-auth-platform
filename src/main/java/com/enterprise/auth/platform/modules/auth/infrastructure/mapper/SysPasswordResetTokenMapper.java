package com.enterprise.auth.platform.modules.auth.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.auth.infrastructure.entity.SysPasswordResetTokenEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysPasswordResetTokenMapper extends BaseMapper<SysPasswordResetTokenEntity> {
}