package com.enterprise.auth.platform.modules.notification.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.notification.infrastructure.entity.SysUserNotificationEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserNotificationMapper extends BaseMapper<SysUserNotificationEntity> {
}