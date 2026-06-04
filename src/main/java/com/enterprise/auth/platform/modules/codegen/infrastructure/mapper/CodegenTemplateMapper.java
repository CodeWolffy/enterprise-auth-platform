package com.enterprise.auth.platform.modules.codegen.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.auth.platform.modules.codegen.infrastructure.entity.CodegenTemplateEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CodegenTemplateMapper extends BaseMapper<CodegenTemplateEntity> {
}