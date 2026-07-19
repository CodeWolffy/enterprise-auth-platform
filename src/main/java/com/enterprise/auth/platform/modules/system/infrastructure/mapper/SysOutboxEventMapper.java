package com.enterprise.auth.platform.modules.system.infrastructure.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysOutboxEventEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysOutboxEventMapper extends BaseMapper<SysOutboxEventEntity> {

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE sys_outbox_event
            SET status = 'PENDING',
                next_retry_at = #{now},
                last_error = 'processing timeout; recovered for retry',
                updated_at = UTC_TIMESTAMP(3)
            WHERE status = 'PROCESSING'
              AND updated_at < #{cutoff}
            """)
    int recoverStaleProcessing(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM sys_outbox_event
            WHERE status = 'PENDING'
              AND (next_retry_at IS NULL OR next_retry_at <= #{now})
            ORDER BY id ASC
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<SysOutboxEventEntity> claimCandidates(@Param("now") Instant now, @Param("limit") int limit);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE sys_outbox_event
            SET status = 'PROCESSING',
                attempts = attempts + 1,
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id}
              AND status = 'PENDING'
            """)
    int markProcessing(@Param("id") Long id);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE sys_outbox_event
            SET status = 'DONE',
                payload_json = CASE
                    WHEN event_type = 'PASSWORD_RESET_MAIL' THEN '{"redacted":true}'
                    ELSE payload_json
                END,
                last_error = NULL,
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int markDone(@Param("id") Long id);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE sys_outbox_event
            SET status = #{status},
                payload_json = CASE
                    WHEN #{status} = 'DEAD' AND event_type = 'PASSWORD_RESET_MAIL' THEN '{"redacted":true}'
                    ELSE payload_json
                END,
                next_retry_at = #{nextRetryAt},
                last_error = #{lastError},
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int markRetryOrDead(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("lastError") String lastError
    );
}
