package com.enterprise.auth.platform.modules.notification.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.notification.infrastructure.entity.SysUserNotificationEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserNotificationMapper extends BaseMapper<SysUserNotificationEntity> {

    @Insert("""
            <script>
            INSERT IGNORE INTO sys_user_notification(
                tenant_id, recipient_user_id, scenario_code, source_type, source_id,
                biz_type, biz_id, title, content, level, link,
                action_payload_json, metadata_json, dedup_key, read_at, expires_at,
                created_by, updated_by, deleted, created_at, updated_at
            ) VALUES
            <foreach collection="notifications" item="item" separator=",">
                (
                    #{item.tenantId}, #{item.recipientUserId}, #{item.scenarioCode},
                    #{item.sourceType}, #{item.sourceId}, #{item.bizType}, #{item.bizId},
                    #{item.title}, #{item.content}, #{item.level}, #{item.link},
                    #{item.actionPayloadJson}, #{item.metadataJson}, #{item.dedupKey},
                    #{item.readAt}, #{item.expiresAt}, #{item.createdBy}, #{item.updatedBy},
                    #{item.deleted}, #{item.createdAt}, #{item.updatedAt}
                )
            </foreach>
            </script>
            """)
    int batchInsertIgnore(@Param("notifications") List<SysUserNotificationEntity> notifications);

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
