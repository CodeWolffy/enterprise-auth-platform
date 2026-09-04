package com.enterprise.auth.platform.modules.workflow.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskUrgeEntity;
import com.enterprise.auth.platform.modules.workflow.infrastructure.projection.WorkflowTaskUrgeCountProjection;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WfTaskUrgeMapper extends BaseMapper<WfTaskUrgeEntity> {

    @Select("""
            <script>
            SELECT task_id, COUNT(*) AS urge_count
            FROM wf_task_urge
            WHERE tenant_id = #{tenantId}
              AND deleted = 0
              AND task_id IN
              <foreach collection="taskIds" item="taskId" open="(" separator="," close=")">
                #{taskId}
              </foreach>
            GROUP BY task_id
            </script>
            """)
    List<WorkflowTaskUrgeCountProjection> countByTaskIds(
            @Param("tenantId") String tenantId,
            @Param("taskIds") Collection<Long> taskIds);
}
