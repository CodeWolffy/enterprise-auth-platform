package com.enterprise.auth.platform.modules.role.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RolePayloadCodec {

    private final ObjectMapper objectMapper;

    public RolePayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String writeDeptIds(Collection<Long> deptIds) {
        try {
            Set<Long> normalized = new LinkedHashSet<>();
            if (deptIds != null) {
                deptIds.stream()
                        .filter(java.util.Objects::nonNull)
                        .forEach(normalized::add);
            }
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw new IllegalStateException("序列化角色数据范围值失败", ex);
        }
    }

    public Set<Long> readDeptIds(String dataScopeValueJson) {
        if (!StringUtils.hasText(dataScopeValueJson)) {
            return Set.of();
        }
        try {
            Set<Long> values = objectMapper.readValue(dataScopeValueJson, new TypeReference<LinkedHashSet<Long>>() {
            });
            return values == null ? Set.of() : values;
        } catch (Exception ignored) {
            return Set.of();
        }
    }
}
