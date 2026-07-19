package com.enterprise.auth.platform.modules.log.infrastructure.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLoginLogEntity;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLogEntity> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT ranges.day_key AS dayKey,
                   COALESCE(SUM(CASE WHEN logs.status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS loginCount,
                   COALESCE(SUM(CASE WHEN logs.status = 'FAILED' THEN 1 ELSE 0 END), 0) AS loginFailedCount
            FROM (
              <foreach collection="ranges" item="range" separator=" UNION ALL ">
                SELECT #{range.dayKey} AS day_key,
                       #{range.fromInclusive} AS from_at,
                       #{range.toExclusive} AS to_at
              </foreach>
            ) ranges
            LEFT JOIN sys_login_log logs
              ON logs.created_at &gt;= ranges.from_at
             AND logs.created_at &lt; ranges.to_at
             AND logs.deleted = 0
            <if test="tenantId != null and tenantId != ''">
             AND logs.tenant_id = #{tenantId}
            </if>
            <if test="!platformScope and visibleUsernames != null">
              <choose>
                <when test="visibleUsernames.size() == 0">
             AND 1 = 0
                </when>
                <otherwise>
             AND logs.user_name IN
                  <foreach collection="visibleUsernames" item="username" open="(" separator="," close=")">
                    #{username}
                  </foreach>
                </otherwise>
              </choose>
            </if>
            GROUP BY ranges.day_key, ranges.from_at
            ORDER BY ranges.from_at
            </script>
            """)
    List<LogDailyAggregateRow> selectDailyTrend(
            @Param("tenantId") String tenantId,
            @Param("platformScope") boolean platformScope,
            @Param("visibleUsernames") Set<String> visibleUsernames,
            @Param("ranges") List<LogTrendRange> ranges
    );
}
