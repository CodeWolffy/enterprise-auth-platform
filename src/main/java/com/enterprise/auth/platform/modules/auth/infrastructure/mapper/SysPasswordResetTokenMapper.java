package com.enterprise.auth.platform.modules.auth.infrastructure.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.auth.infrastructure.entity.SysPasswordResetTokenEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysPasswordResetTokenMapper extends BaseMapper<SysPasswordResetTokenEntity> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM sys_password_reset_token
            WHERE token_hash = #{tokenHash}
              AND deleted = 0
            LIMIT 1
            """)
    SysPasswordResetTokenEntity selectByTokenHash(@Param("tokenHash") String tokenHash);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM sys_password_reset_token
            WHERE token_hash = #{tokenHash}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    SysPasswordResetTokenEntity selectByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE sys_password_reset_token
            SET revoked_at = #{revokedAt},
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND deleted = 0
              AND used_at IS NULL
              AND revoked_at IS NULL
            """)
    int revokeIfActive(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt, @Param("updatedBy") String updatedBy);
}
