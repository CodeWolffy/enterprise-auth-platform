package com.enterprise.auth.platform.modules.notification.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.observability.PlatformMetrics;
import com.enterprise.auth.platform.modules.notification.infrastructure.entity.SysUserNotificationEntity;
import com.enterprise.auth.platform.modules.notification.infrastructure.mapper.SysUserNotificationMapper;
import com.enterprise.auth.platform.modules.role.application.RoleQueryFacade;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);
    private static final int BATCH_INSERT_SIZE = 500;

    private final SysUserNotificationMapper notificationMapper;
    private final RoleQueryFacade roleQueryFacade;
    private final UserQueryFacade userQueryFacade;
    private final ObjectMapper objectMapper;
    private final NotificationSseRegistry sseRegistry;
    private final PlatformMetrics metrics;

    public NotificationPublisher(
            SysUserNotificationMapper notificationMapper,
            RoleQueryFacade roleQueryFacade,
            UserQueryFacade userQueryFacade,
            ObjectMapper objectMapper,
            NotificationSseRegistry sseRegistry,
            PlatformMetrics metrics
    ) {
        this.notificationMapper = notificationMapper;
        this.roleQueryFacade = roleQueryFacade;
        this.userQueryFacade = userQueryFacade;
        this.objectMapper = objectMapper;
        this.sseRegistry = sseRegistry;
        this.metrics = metrics;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long publish(NotificationPublishCommand command) {
        try {
            PublishOutcome outcome = doPublish(command);
            scheduleSseAfterCommit(outcome.ssePushes());
            return outcome.published();
        } catch (RuntimeException exception) {
            metrics.recordNotificationPublish(command == null ? null : command.scenarioCode(), "failure", 0);
            throw exception;
        }
    }

    private void scheduleSseAfterCommit(List<SsePush> pushes) {
        if (pushes == null || pushes.isEmpty()) {
            return;
        }
        Runnable task = () -> {
            for (SsePush push : pushes) {
                sseRegistry.send(push.tenantId(), push.userId(), push.view());
            }
        };
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        task.run();
                    }
                }
        );
    }

    private PublishOutcome doPublish(NotificationPublishCommand command) {
        if (command == null || !StringUtils.hasText(command.tenantId())) {
            metrics.recordNotificationPublish(null, "ignored", 0);
            return PublishOutcome.empty();
        }
        String tenantId = command.tenantId().trim();
        String title = limit(command.title(), 128);
        if (!StringUtils.hasText(title)) {
            metrics.recordNotificationPublish(command.scenarioCode(), "ignored", 0);
            return PublishOutcome.empty();
        }
        String scenarioCode = limit(command.scenarioCode(), 64);
        String sourceType = limit(command.sourceType(), 64);
        String sourceId = limit(command.sourceId(), 128);
        String bizType = limit(command.bizType(), 64);
        String bizId = limit(command.bizId(), 128);
        String content = limit(command.content(), 1000);
        String level = limit(command.level(), 32);
        String link = limit(command.link(), 255);
        String actionPayloadJson = toJson(command.actionPayload());
        String metadataJson = toJson(command.metadata());
        String dedupKey = limit(command.dedupKey(), 191);
        String createdBy = limit(command.createdBy(), 64);
        Set<Long> recipientUserIds = resolveRecipients(command);
        if (recipientUserIds.isEmpty()) {
            metrics.recordNotificationPublish(command.scenarioCode(), "no_recipients", 0);
            return PublishOutcome.empty();
        }
        long published = 0;
        List<SsePush> ssePushes = new ArrayList<>();
        List<SysUserNotificationEntity> pendingBatch = new ArrayList<>(Math.min(recipientUserIds.size(), BATCH_INSERT_SIZE));
        Instant createdAt = TimeSupport.now();
        for (Long recipientUserId : recipientUserIds) {
            if (recipientUserId == null) {
                continue;
            }
            SysUserNotificationEntity entity = buildEntity(
                    tenantId,
                    recipientUserId,
                    scenarioCode,
                    sourceType,
                    sourceId,
                    bizType,
                    bizId,
                    title,
                    content,
                    level,
                    link,
                    actionPayloadJson,
                    metadataJson,
                    dedupKey,
                    command.expiresAt(),
                    createdBy,
                    createdAt);
            if (sseRegistry.hasActiveConnection(tenantId, recipientUserId)) {
                if (insertOnly(entity) > 0) {
                    published += 1;
                    ssePushes.add(new SsePush(tenantId, recipientUserId, NotificationView.from(entity)));
                }
                continue;
            }
            pendingBatch.add(entity);
            if (pendingBatch.size() >= BATCH_INSERT_SIZE) {
                published += batchInsertIgnore(pendingBatch);
                pendingBatch.clear();
            }
        }
        published += batchInsertIgnore(pendingBatch);
        metrics.recordNotificationPublish(command.scenarioCode(), "success", published);
        return new PublishOutcome(published, ssePushes);
    }

    private long insertOnly(SysUserNotificationEntity entity) {
        try {
            notificationMapper.insert(entity);
            return 1;
        } catch (DuplicateKeyException ex) {
            return 0;
        }
    }

    private record SsePush(String tenantId, Long userId, NotificationView view) {
    }

    private record PublishOutcome(long published, List<SsePush> ssePushes) {
        static PublishOutcome empty() {
            return new PublishOutcome(0, List.of());
        }
    }

    private Set<Long> resolveRecipients(NotificationPublishCommand command) {
        Set<Long> userIds = new LinkedHashSet<>(userQueryFacade.listEnabledUserIds(command.tenantId(), command.recipientUserIds()));
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

    private SysUserNotificationEntity buildEntity(
            String tenantId,
            Long recipientUserId,
            String scenarioCode,
            String sourceType,
            String sourceId,
            String bizType,
            String bizId,
            String title,
            String content,
            String level,
            String link,
            String actionPayloadJson,
            String metadataJson,
            String dedupKey,
            Instant expiresAt,
            String createdBy,
            Instant createdAt
    ) {
        SysUserNotificationEntity entity = new SysUserNotificationEntity();
        entity.setTenantId(tenantId);
        entity.setRecipientUserId(recipientUserId);
        entity.setScenarioCode(scenarioCode);
        entity.setSourceType(sourceType);
        entity.setSourceId(sourceId);
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setLevel(level);
        entity.setLink(link);
        entity.setActionPayloadJson(actionPayloadJson);
        entity.setMetadataJson(metadataJson);
        entity.setDedupKey(dedupKey);
        entity.setExpiresAt(expiresAt);
        entity.setCreatedBy(createdBy);
        entity.setUpdatedBy(createdBy);
        entity.setDeleted(0);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(createdAt);
        return entity;
    }

    private long batchInsertIgnore(List<SysUserNotificationEntity> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return 0;
        }
        return notificationMapper.batchInsertIgnore(notifications);
    }

    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.debug("通知载荷序列化失败，将忽略该载荷。keys={}，error={}", value.keySet(), ex.getMessage());
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
