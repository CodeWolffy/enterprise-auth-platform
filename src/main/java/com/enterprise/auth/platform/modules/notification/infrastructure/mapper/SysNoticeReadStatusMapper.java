package com.enterprise.auth.platform.modules.notification.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.notification.infrastructure.entity.SysNoticeReadStatusEntity;
import com.enterprise.auth.platform.modules.notification.infrastructure.projection.NoticeBroadcastProjection;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysNoticeReadStatusMapper extends BaseMapper<SysNoticeReadStatusEntity> {

    @Select("""
            <script>
            SELECT COUNT(*)
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
              AND (rs.cleared_at IS NULL)
            <if test="read != null and read">
              AND rs.read_at IS NOT NULL
            </if>
            <if test="read != null and !read">
              AND rs.read_at IS NULL
            </if>
            </script>
            """)
    long countVisibleBroadcasts(
            @Param("tenantId") String tenantId,
            @Param("userId") Long userId,
            @Param("read") Boolean read,
            @Param("now") Instant now);

    @Select("""
            <script>
            SELECT
                n.id,
                n.tenant_id,
                n.notice_title,
                n.notice_content,
                n.publish_time,
                n.created_by,
                n.created_at,
                rs.read_at
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
              AND (rs.cleared_at IS NULL)
            <if test="read != null and read">
              AND rs.read_at IS NOT NULL
            </if>
            <if test="read != null and !read">
              AND rs.read_at IS NULL
            </if>
            ORDER BY (rs.read_at IS NULL) DESC,
                     COALESCE(n.publish_time, n.created_at) DESC,
                     n.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<NoticeBroadcastProjection> listVisibleBroadcasts(
            @Param("tenantId") String tenantId,
            @Param("userId") Long userId,
            @Param("read") Boolean read,
            @Param("now") Instant now,
            @Param("limit") int limit);

    @Select("""
            SELECT
                n.id,
                n.tenant_id,
                n.notice_title,
                n.notice_content,
                n.publish_time,
                n.created_by,
                n.created_at,
                rs.read_at
            FROM sys_notice n
            LEFT JOIN sys_notice_read_status rs
              ON rs.tenant_id = n.tenant_id
             AND rs.notice_id = n.id
             AND rs.user_id = #{userId}
             AND rs.deleted = 0
            WHERE n.tenant_id = #{tenantId}
              AND n.id = #{noticeId}
              AND n.deleted = 0
              AND n.published = 1
              AND (n.publish_time IS NULL OR n.publish_time <= #{now})
              AND (rs.cleared_at IS NULL)
            LIMIT 1
            """)
    NoticeBroadcastProjection findVisibleBroadcast(
            @Param("tenantId") String tenantId,
            @Param("userId") Long userId,
            @Param("noticeId") Long noticeId,
            @Param("now") Instant now);

    @Insert("""
            INSERT INTO sys_notice_read_status(
                tenant_id, notice_id, user_id, read_at, cleared_at,
                created_by, updated_by, deleted, created_at, updated_at
            ) VALUES (
                #{tenantId}, #{noticeId}, #{userId}, #{readAt}, NULL,
                #{operator}, #{operator}, 0, #{readAt}, #{readAt}
            )
            ON DUPLICATE KEY UPDATE
                read_at = COALESCE(read_at, VALUES(read_at)),
                updated_by = VALUES(updated_by),
                updated_at = VALUES(updated_at)
            """)
    int markBroadcastRead(
            @Param("tenantId") String tenantId,
            @Param("noticeId") Long noticeId,
            @Param("userId") Long userId,
            @Param("readAt") Instant readAt,
            @Param("operator") String operator);

    @Insert("""
            INSERT IGNORE INTO sys_notice_read_status(
                tenant_id, notice_id, user_id, read_at, cleared_at,
                created_by, updated_by, deleted, created_at, updated_at
            )
            SELECT n.tenant_id, n.id, #{userId}, #{readAt}, NULL,
                   #{operator}, #{operator}, 0, #{readAt}, #{readAt}
            FROM sys_notice n
            LEFT JOIN sys_notice_read_status rs
              ON rs.tenant_id = n.tenant_id
             AND rs.notice_id = n.id
             AND rs.user_id = #{userId}
             AND rs.deleted = 0
            WHERE n.tenant_id = #{tenantId}
              AND n.deleted = 0
              AND n.published = 1
              AND (n.publish_time IS NULL OR n.publish_time <= #{readAt})
              AND rs.id IS NULL
            """)
    int markAllUnreadBroadcastsRead(
            @Param("tenantId") String tenantId,
            @Param("userId") Long userId,
            @Param("readAt") Instant readAt,
            @Param("operator") String operator);

    @Update("""
            UPDATE sys_notice_read_status
            SET cleared_at = #{clearedAt},
                updated_by = #{operator},
                updated_at = #{clearedAt}
            WHERE tenant_id = #{tenantId}
              AND user_id = #{userId}
              AND deleted = 0
              AND read_at IS NOT NULL
              AND cleared_at IS NULL
            """)
    int clearReadBroadcasts(
            @Param("tenantId") String tenantId,
            @Param("userId") Long userId,
            @Param("clearedAt") Instant clearedAt,
            @Param("operator") String operator);
}
