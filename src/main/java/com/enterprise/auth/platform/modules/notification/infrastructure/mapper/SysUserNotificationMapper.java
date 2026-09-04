package com.enterprise.auth.platform.modules.notification.infrastructure.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.notification.infrastructure.entity.SysUserNotificationEntity;
import com.enterprise.auth.platform.modules.notification.infrastructure.projection.NotificationInboxProjection;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserNotificationMapper extends BaseMapper<SysUserNotificationEntity> {

    /**
     * 统计统一收件箱中的直接通知和广播通知。
     * <p>过滤条件与分页投影保持一致，避免两类通知分别计数后与结果集不一致。</p>
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM (
                SELECT u.id, u.read_at
                FROM sys_user_notification u
                WHERE u.tenant_id = #{tenantId}
                  AND u.recipient_user_id = #{userId}
                  AND u.deleted = 0
                  AND (u.expires_at IS NULL OR u.expires_at &gt; #{now})
                UNION ALL
                SELECT n.id, rs.read_at
                FROM sys_notice n
                LEFT JOIN sys_notice_read_status rs
                  ON rs.tenant_id = n.tenant_id
                 AND rs.notice_id = n.id
                 AND rs.user_id = #{userId}
                 AND rs.deleted = 0
                WHERE n.tenant_id = #{tenantId}
                  AND n.deleted = 0
                  AND n.published = 1
                  AND (n.publish_time IS NULL OR n.publish_time &lt;= #{now})
                  AND rs.cleared_at IS NULL
            ) inbox
            WHERE 1 = 1
            <if test="read != null and read">
              AND inbox.read_at IS NOT NULL
            </if>
            <if test="read != null and !read">
              AND inbox.read_at IS NULL
            </if>
            </script>
            """)
    long countVisibleNotifications(
            @Param("tenantId") String tenantId,
            @Param("userId") Long userId,
            @Param("read") Boolean read,
            @Param("now") Instant now);

    /**
     * 统一投影并在数据库完成排序、分页，避免两路查询分别取 offset+size 后再内存合并。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT
                inbox.id,
                inbox.broadcast_flag,
                inbox.scenario_code,
                inbox.source_type,
                inbox.source_id,
                inbox.biz_type,
                inbox.biz_id,
                inbox.title,
                inbox.content,
                inbox.level,
                inbox.link,
                inbox.action_payload_json,
                inbox.metadata_json,
                inbox.read_at,
                inbox.expires_at,
                inbox.created_at,
                inbox.notice_title,
                inbox.notice_content,
                inbox.publish_time
            FROM (
                SELECT
                    u.id AS id,
                    0 AS broadcast_flag,
                    u.scenario_code AS scenario_code,
                    u.source_type AS source_type,
                    u.source_id AS source_id,
                    u.biz_type AS biz_type,
                    u.biz_id AS biz_id,
                    u.title AS title,
                    u.content AS content,
                    u.level AS level,
                    u.link AS link,
                    u.action_payload_json AS action_payload_json,
                    u.metadata_json AS metadata_json,
                    u.read_at AS read_at,
                    u.expires_at AS expires_at,
                    u.created_at AS created_at,
                    NULL AS notice_title,
                    NULL AS notice_content,
                    NULL AS publish_time
                FROM sys_user_notification u
                WHERE u.tenant_id = #{tenantId}
                  AND u.recipient_user_id = #{userId}
                  AND u.deleted = 0
                  AND (u.expires_at IS NULL OR u.expires_at &gt; #{now})
                UNION ALL
                SELECT
                    -n.id AS id,
                    1 AS broadcast_flag,
                    NULL AS scenario_code,
                    NULL AS source_type,
                    NULL AS source_id,
                    NULL AS biz_type,
                    NULL AS biz_id,
                    NULL AS title,
                    NULL AS content,
                    NULL AS level,
                    NULL AS link,
                    NULL AS action_payload_json,
                    NULL AS metadata_json,
                    rs.read_at AS read_at,
                    NULL AS expires_at,
                    COALESCE(n.publish_time, n.created_at) AS created_at,
                    n.notice_title AS notice_title,
                    n.notice_content AS notice_content,
                    n.publish_time AS publish_time
                FROM sys_notice n
                LEFT JOIN sys_notice_read_status rs
                  ON rs.tenant_id = n.tenant_id
                 AND rs.notice_id = n.id
                 AND rs.user_id = #{userId}
                 AND rs.deleted = 0
                WHERE n.tenant_id = #{tenantId}
                  AND n.deleted = 0
                  AND n.published = 1
                  AND (n.publish_time IS NULL OR n.publish_time &lt;= #{now})
                  AND rs.cleared_at IS NULL
            ) inbox
            WHERE 1 = 1
            <if test="read != null and read">
              AND inbox.read_at IS NOT NULL
            </if>
            <if test="read != null and !read">
              AND inbox.read_at IS NULL
            </if>
            ORDER BY (inbox.read_at IS NULL) DESC,
                     inbox.created_at DESC,
                     ABS(inbox.id) DESC,
                     inbox.broadcast_flag ASC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<NotificationInboxProjection> listVisibleNotifications(
            @Param("tenantId") String tenantId,
            @Param("userId") Long userId,
            @Param("read") Boolean read,
            @Param("now") Instant now,
            @Param("offset") int offset,
            @Param("limit") int limit);

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
