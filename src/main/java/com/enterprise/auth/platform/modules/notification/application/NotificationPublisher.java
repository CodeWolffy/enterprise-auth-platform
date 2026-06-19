package com.enterprise.auth.platform.modules.notification.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.notification.infrastructure.entity.SysUserNotificationEntity;
import com.enterprise.auth.platform.modules.notification.infrastructure.mapper.SysUserNotificationMapper;
import com.enterprise.auth.platform.modules.role.application.RoleQueryFacade;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NotificationPublisher {

    private final SysUserNotificationMapper notificationMapper;
    private final RoleQueryFacade roleQueryFacade;
    private final UserQueryFacade userQueryFacade;
    private final ObjectMapper objectMapper;
    private final NotificationSseRegistry sseRegistry;

    public NotificationPublisher(
            SysUserNotificationMapper notificationMapper,
            RoleQueryFacade roleQueryFacade,
            UserQueryFacade userQueryFacade,
            ObjectMapper objectMapper,
            NotificationSseRegistry sseRegistry
    ) {
        this.notificationMapper = notificationMapper;
        this.roleQueryFacade = roleQueryFacade;
        this.userQueryFacade = userQueryFacade;
        this.objectMapper = objectMapper;
        this.sseRegistry = sseRegistry;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long publish(NotificationPublishCommand command) {
        if (command == null || !StringUtils.hasText(command.tenantId())) {
            return 0;
        }
        String title = limit(command.title(), 128);
        if (!StringUtils.hasText(title)) {
            return 0;
        }
        String content = limit(command.content(), 1000);
        String link = limit(command.link(), 255);
        Set<Long> recipientUserIds = resolveRecipients(command);
        if (recipientUserIds.isEmpty()) {
            return 0;
        }
        long published = 0;
        for (Long recipientUserId : recipientUserIds) {
            if (recipientUserId == null || duplicated(command.tenantId(), recipientUserId, command.dedupKey())) {
                continue;
            }
            SysUserNotificationEntity entity = new SysUserNotificationEntity();
            entity.setTenantId(command.tenantId().trim());
            entity.setRecipientUserId(recipientUserId);
            entity.setScenarioCode(limit(command.scenarioCode(), 64));
            entity.setSourceType(limit(command.sourceType(), 64));
            entity.setSourceId(limit(command.sourceId(), 128));
            entity.setBizType(limit(command.bizType(), 64));
            entity.setBizId(limit(command.bizId(), 128));
            entity.setTitle(title);
            entity.setContent(content);
            entity.setLevel(limit(command.level(), 32));
            entity.setLink(link);
            entity.setActionPayloadJson(toJson(command.actionPayload()));
            entity.setMetadataJson(toJson(command.metadata()));
            entity.setDedupKey(limit(command.dedupKey(), 191));
            entity.setExpiresAt(command.expiresAt());
            entity.setCreatedBy(limit(command.createdBy(), 64));
            entity.setUpdatedBy(limit(command.createdBy(), 64));
            try {
                notificationMapper.insert(entity);
                published++;
                sseRegistry.send(command.tenantId(), recipientUserId, NotificationView.from(entity));
            } catch (DuplicateKeyException ex) {
                // 已由唯一约束兜底保证同一用户同一去重键只写入一次。
            }
        }
        return published;
    }

    private Set<Long> resolveRecipients(NotificationPublishCommand command) {
        Set<Long> userIds = userQueryFacade.listEnabledUserIds(command.tenantId(), command.recipientUserIds());
        Set<String> roleCodes = new LinkedHashSet<>(command.recipientRoleCodes());
        if (command.tenantAdmins()) {
            roleCodes.add("TENANT_ADMIN");
        }
        userIds.addAll(resolveRoleMembers(command.tenantId(), roleCodes));
        return userIds;
    }

    private Set<Long> resolveRoleMembers(String tenantId, Set<String> roleCodes) {
        if (!StringUtils.hasText(tenantId) || roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        Set<Long> roleIds = roleQueryFacade.listRoleIdsByCodes(tenantId, roleCodes);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> roleMemberIds = userQueryFacade.listUserIdsByRoles(tenantId, roleIds);
        return userQueryFacade.listEnabledUserIds(tenantId, roleMemberIds);
    }

    private boolean duplicated(String tenantId, Long recipientUserId, String dedupKey) {
        if (!StringUtils.hasText(dedupKey)) {
            return false;
        }
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<SysUserNotificationEntity>()
                .eq(SysUserNotificationEntity::getTenantId, tenantId)
                .eq(SysUserNotificationEntity::getRecipientUserId, recipientUserId)
                .eq(SysUserNotificationEntity::getDedupKey, dedupKey.trim())
                .eq(SysUserNotificationEntity::getDeleted, 0));
        return count != null && count > 0;
    }

    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}