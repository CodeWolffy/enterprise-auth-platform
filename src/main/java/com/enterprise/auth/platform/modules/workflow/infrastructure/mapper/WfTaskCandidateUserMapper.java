package com.enterprise.auth.platform.modules.workflow.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.workflow.infrastructure.entity.WfTaskCandidateUserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WfTaskCandidateUserMapper extends BaseMapper<WfTaskCandidateUserEntity> {
}