package com.enterprise.auth.platform.modules.codegen.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.codegen.infrastructure.entity.CodegenTemplateEntity;
import com.enterprise.auth.platform.modules.codegen.infrastructure.mapper.CodegenTemplateMapper;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CodegenTemplateService {

    private final CodegenTemplateMapper templateMapper;

    public CodegenTemplateService(CodegenTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    public PageResult<CodegenTemplateView> list(String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<CodegenTemplateEntity> wrapper = new LambdaQueryWrapper<CodegenTemplateEntity>()
                .eq(CodegenTemplateEntity::getDeleted, 0)
                .orderByDesc(CodegenTemplateEntity::getBuiltin)
                .orderByAsc(CodegenTemplateEntity::getName);
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            wrapper.and(query -> query.like(CodegenTemplateEntity::getName, like)
                    .or().like(CodegenTemplateEntity::getPathPattern, like)
                    .or().like(CodegenTemplateEntity::getDescription, like));
        }
        long total = templateMapper.selectCount(wrapper);
        int offset = (safePage - 1) * safeSize;
        wrapper.last("limit " + offset + "," + safeSize);
        List<CodegenTemplateEntity> records = templateMapper.selectList(wrapper);
        return PageResult.of(total, safePage, safeSize, CodegenTemplateView.fromAll(records));
    }

    public CodegenTemplateView detail(Long id) {
        CodegenTemplateEntity entity = requireTemplate(id);
        return CodegenTemplateView.from(entity);
    }

    @Transactional
    public CodegenTemplateView create(CodegenTemplateView view) {
        validateName(view.name(), null);
        validateLanguage(view.language());
        validatePattern(view.pathPattern());
        CodegenTemplateEntity entity = new CodegenTemplateEntity();
        entity.setName(view.name().trim());
        entity.setLanguage(view.language().toLowerCase());
        entity.setPathPattern(view.pathPattern().trim());
        entity.setContent(view.content());
        entity.setDescription(StringUtils.hasText(view.description()) ? view.description().trim() : null);
        entity.setBuiltin(0);
        templateMapper.insert(entity);
        return CodegenTemplateView.from(entity);
    }

    @Transactional
    public CodegenTemplateView update(Long id, CodegenTemplateView view) {
        CodegenTemplateEntity entity = requireTemplate(id);
        if (entity.getBuiltin() != null && entity.getBuiltin() == 1) {
            throw new BusinessException("BUILTIN_READONLY", "内置模板不允许修改");
        }
        validateName(view.name(), id);
        validateLanguage(view.language());
        validatePattern(view.pathPattern());
        entity.setName(view.name().trim());
        entity.setLanguage(view.language().toLowerCase());
        entity.setPathPattern(view.pathPattern().trim());
        entity.setContent(view.content());
        entity.setDescription(StringUtils.hasText(view.description()) ? view.description().trim() : null);
        templateMapper.updateById(entity);
        return CodegenTemplateView.from(entity);
    }

    @Transactional
    public void delete(Long id) {
        CodegenTemplateEntity entity = requireTemplate(id);
        if (entity.getBuiltin() != null && entity.getBuiltin() == 1) {
            throw new BusinessException("BUILTIN_READONLY", "内置模板不允许删除");
        }
        templateMapper.deleteById(id);
    }

    public List<CodegenTemplateEntity> matchTemplates(String language) {
        if (!StringUtils.hasText(language)) {
            return List.of();
        }
        return templateMapper.selectList(new LambdaQueryWrapper<CodegenTemplateEntity>()
                .eq(CodegenTemplateEntity::getDeleted, 0)
                .eq(CodegenTemplateEntity::getLanguage, language.toLowerCase())
                .orderByDesc(CodegenTemplateEntity::getBuiltin)
                .orderByAsc(CodegenTemplateEntity::getId));
    }

    public String applyTemplate(String content, java.util.Map<String, Object> variables) {
        if (!StringUtils.hasText(content) || variables == null) {
            return content;
        }
        String result = content;
        for (java.util.Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            result = result.replace(placeholder, value);
        }
        return result;
    }

    public boolean patternMatches(String pattern, String path) {
        if (!StringUtils.hasText(pattern) || !StringUtils.hasText(path)) {
            return false;
        }
        if (pattern.equals(path)) {
            return true;
        }
        try {
            return Pattern.compile(pattern).matcher(path).find();
        } catch (Exception ex) {
            return path.contains(pattern);
        }
    }

    public String renderBody(String language, String path, String defaultContent, java.util.Map<String, Object> variables) {
        List<CodegenTemplateEntity> templates = matchTemplates(language);
        for (CodegenTemplateEntity template : templates) {
            if (patternMatches(template.getPathPattern(), path)) {
                return applyTemplate(template.getContent(), variables);
            }
        }
        return applyTemplate(defaultContent, variables);
    }

    private CodegenTemplateEntity requireTemplate(Long id) {
        if (id == null) {
            throw new BusinessException("VALIDATION_ERROR", "模板 ID 不能为空");
        }
        CodegenTemplateEntity entity = templateMapper.selectOne(new LambdaQueryWrapper<CodegenTemplateEntity>()
                .eq(CodegenTemplateEntity::getId, id)
                .eq(CodegenTemplateEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("NOT_FOUND", "代码生成模板不存在");
        }
        return entity;
    }

    private void validateName(String name, Long selfId) {
        if (!StringUtils.hasText(name) || name.length() > 128) {
            throw new BusinessException("VALIDATION_ERROR", "模板名称不合法");
        }
        Long count = templateMapper.selectCount(new LambdaQueryWrapper<CodegenTemplateEntity>()
                .eq(CodegenTemplateEntity::getName, name.trim())
                .eq(CodegenTemplateEntity::getDeleted, 0)
                .ne(selfId != null, CodegenTemplateEntity::getId, selfId == null ? -1L : selfId));
        if (count != null && count > 0) {
            throw new BusinessException("CONFLICT", "模板名称已存在");
        }
    }

    private void validateLanguage(String language) {
        if (!StringUtils.hasText(language) || !language.matches("^(java|typescript|vue)$")) {
            throw new BusinessException("VALIDATION_ERROR", "模板语言仅支持 java/typescript/vue");
        }
    }

    private void validatePattern(String pathPattern) {
        if (!StringUtils.hasText(pathPattern) || pathPattern.length() > 255) {
            throw new BusinessException("VALIDATION_ERROR", "路径匹配规则不合法");
        }
    }
}