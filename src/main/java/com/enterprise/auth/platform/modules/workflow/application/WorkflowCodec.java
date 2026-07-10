package com.enterprise.auth.platform.modules.workflow.application;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.workflow.domain.WorkflowStepDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 工作流 JSON 序列化助手：负责流程步骤、候选人、候选组与变量快照的读写。
 * 纯序列化逻辑，供各工作流应用服务共享。
 */
@Component
class WorkflowCodec {

    private static final TypeReference<List<WorkflowStepDefinition>> STEP_LIST_TYPE = new TypeReference<>() { };
    private static final TypeReference<Set<Long>> LONG_SET_TYPE = new TypeReference<>() { };
    private static final TypeReference<Set<String>> STRING_SET_TYPE = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    WorkflowCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("JSON_SERIALIZE_FAILED", "数据序列化失败");
        }
    }

    List<WorkflowStepDefinition> readSteps(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<WorkflowStepDefinition> steps = objectMapper.readValue(json, STEP_LIST_TYPE);
            return steps == null ? List.of() : steps;
        } catch (JsonProcessingException ex) {
            throw new BusinessException("JSON_PARSE_FAILED", "流程步骤解析失败");
        }
    }

    Set<Long> readLongSet(String json) {
        if (!StringUtils.hasText(json)) {
            return Set.of();
        }
        try {
            Set<Long> values = objectMapper.readValue(json, LONG_SET_TYPE);
            return values == null ? Set.of() : values;
        } catch (JsonProcessingException ex) {
            throw new BusinessException("JSON_PARSE_FAILED", "候选人解析失败");
        }
    }

    Set<String> readStringSet(String json) {
        if (!StringUtils.hasText(json)) {
            return Set.of();
        }
        try {
            Set<String> values = objectMapper.readValue(json, STRING_SET_TYPE);
            return values == null ? Set.of() : values;
        } catch (JsonProcessingException ex) {
            throw new BusinessException("JSON_PARSE_FAILED", "候选组解析失败");
        }
    }

    Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> value = objectMapper.readValue(json, MAP_TYPE);
            return value == null ? Map.of() : value;
        } catch (JsonProcessingException ex) {
            throw new BusinessException("JSON_PARSE_FAILED", "变量快照解析失败");
        }
    }

    Map<String, Object> snapshotVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        variables.forEach((key, value) -> {
            if (StringUtils.hasText(key)) {
                snapshot.put(key.trim(), value);
            }
        });
        return snapshot;
    }
}
