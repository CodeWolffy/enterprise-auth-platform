package com.enterprise.auth.platform.modules.workflow.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskCandidateRoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WfTaskCandidateRoleMapper extends BaseMapper<WfTaskCandidateRoleEntity> {

    @Insert({
            "<script>",
            "INSERT IGNORE INTO wf_task_candidate_role (tenant_id, task_id, role_code) VALUES",
            "<foreach collection='links' item='link' separator=','>",
            "(#{link.tenantId}, #{link.taskId}, #{link.roleCode})",
            "</foreach>",
            "</script>"
    })
    int insertIgnoreBatch(@Param("links") List<WfTaskCandidateRoleEntity> links);
}
