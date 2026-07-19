package com.enterprise.auth.platform.modules.workflow.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskCandidateUserEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WfTaskCandidateUserMapper extends BaseMapper<WfTaskCandidateUserEntity> {

    @Insert({
            "<script>",
            "INSERT IGNORE INTO wf_task_candidate_user (tenant_id, task_id, user_id) VALUES",
            "<foreach collection='links' item='link' separator=','>",
            "(#{link.tenantId}, #{link.taskId}, #{link.userId})",
            "</foreach>",
            "</script>"
    })
    int insertIgnoreBatch(@Param("links") List<WfTaskCandidateUserEntity> links);
}
