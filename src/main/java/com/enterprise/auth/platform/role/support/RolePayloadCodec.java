package com.enterprise.auth.platform.role.support;

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

    public String writePermissionCodes(Collection<String> permissionCodes) {
        try {
            Set<String> normalized = new LinkedHashSet<>();
            if (permissionCodes != null) {
                permissionCodes.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .forEach(normalized::add);
            }
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize role permissions", ex);
        }
    }

    public Set<String> readPermissionCodes(String permissionsJson) {
        return readStringSet(permissionsJson);
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
            throw new IllegalStateException("Failed to serialize role data scope values", ex);
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

    private Set<String> readStringSet(String json) {
        if (!StringUtils.hasText(json)) {
            return Set.of();
        }
        try {
            Set<String> values = objectMapper.readValue(json, new TypeReference<LinkedHashSet<String>>() {
            });
            if (values == null) {
                return Set.of();
            }
            Set<String> normalized = new LinkedHashSet<>();
            values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(normalized::add);
            return normalized;
        } catch (Exception ignored) {
            return Set.of();
        }
    }
}
