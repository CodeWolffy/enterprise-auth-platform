package com.enterprise.auth.platform.modules.notification.domain;

import java.util.LinkedHashSet;
import java.util.Set;

public record NotificationRecipient(
        NotificationRecipientType type,
        Set<Long> userIds,
        Set<String> roleCodes
) {
    public static NotificationRecipient users(Set<Long> userIds) {
        return new NotificationRecipient(NotificationRecipientType.USER, copyUserIds(userIds), Set.of());
    }

    public static NotificationRecipient roles(Set<String> roleCodes) {
        return new NotificationRecipient(NotificationRecipientType.ROLE, Set.of(), copyRoleCodes(roleCodes));
    }

    public static NotificationRecipient tenantAdmins() {
        return new NotificationRecipient(NotificationRecipientType.TENANT_ADMIN, Set.of(), Set.of());
    }

    private static Set<Long> copyUserIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> values = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null) {
                values.add(userId);
            }
        }
        return values;
    }

    private static Set<String> copyRoleCodes(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String roleCode : roleCodes) {
            if (roleCode != null && !roleCode.isBlank()) {
                values.add(roleCode.trim());
            }
        }
        return values;
    }
}