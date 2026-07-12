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
                last_error = NULL,
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int markDone(@Param("id") Long id);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE sys_outbox_event
            SET status = #{status},
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