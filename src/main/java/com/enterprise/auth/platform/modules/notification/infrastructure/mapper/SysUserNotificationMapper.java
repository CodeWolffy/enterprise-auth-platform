package com.enterprise.auth.platform.modules.notification.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.notification.infrastructure.entity.SysUserNotificationEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserNotificationMapper extends BaseMapper<SysUserNotificationEntity> {

    /**
     * 物理删除指定租户+用户的所有已读通知。
     * <p>
     * 该方法绕过 {@code @TableLogic} 进行硬删除，用于应对通知数据量较大的场景，
     * 避免已读通知长期占用存储空间。
     *
     * @param tenantId 租户标识
     * @param userId   接收用户 ID
     * @return 删除条数
     */
    @Delete("DELETE FROM sys_user_notification "
            + "WHERE tenant_id = #{tenantId} "
            + "AND recipient_user_id = #{userId} "
            + "AND read_at IS NOT NULL")
    int hardDeleteReadNotifications(@Param("tenantId") String tenantId, @Param("userId") Long userId);
}