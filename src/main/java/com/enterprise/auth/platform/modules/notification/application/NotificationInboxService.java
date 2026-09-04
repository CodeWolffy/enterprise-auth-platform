package com.enterprise.auth.platform.modules.notification.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.auth.application.SecuritySupport;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.web.PaginationSupport;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.notification.infrastructure.mapper.SysNoticeReadStatusMapper;
import com.enterprise.auth.platform.modules.notification.infrastructure.entity.SysUserNotificationEntity;
import com.enterprise.auth.platform.modules.notification.infrastructure.mapper.SysUserNotificationMapper;
import com.enterprise.auth.platform.modules.notification.infrastructure.projection.NoticeBroadcastProjection;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationInboxService {

    private final SysUserNotificationMapper notificationMapper;
    private final SysNoticeReadStatusMapper noticeReadStatusMapper;
    private final CurrentUserService currentUserService;

    public NotificationInboxService(
            SysUserNotificationMapper notificationMapper,
            SysNoticeReadStatusMapper noticeReadStatusMapper,
            CurrentUserService currentUserService
    ) {
        this.notificationMapper = notificationMapper;
        this.noticeReadStatusMapper = noticeReadStatusMapper;
        this.currentUserService = currentUserService;
    }

    public PageResult<NotificationView> myNotifications(int page, int size, Boolean read) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform(user.tenantId());
        int safePage = PaginationSupport.normalizePage(page);
        int safeSize = PaginationSupport.normalizeSize(size, 100);
        Instant now = TimeSupport.now();
        long total = notificationMapper.countVisibleNotifications(tenantId, user.id(), read, now);
        int offset = (int) Math.min(
                Integer.MAX_VALUE,
                PaginationSupport.offset(safePage, safeSize));
        List<NotificationView> records = notificationMapper
                .listVisibleNotifications(tenantId, user.id(), read, now, offset, safeSize)
                .stream()
                .map(NotificationView::fromInbox)
                .toList();
        return PageResult.of(total, safePage, safeSize, records);
    }

    public long unreadCount() {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform(user.tenantId());
        Instant now = TimeSupport.now();
        return notificationMapper.selectCount(visibleMineWrapper(tenantId, user.id(), false))
                + noticeReadStatusMapper.countVisibleBroadcasts(tenantId, user.id(), false, now);
    }

    @Transactional
    public NotificationView markRead(Long notificationId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform(user.tenantId());
        if (notificationId != null && notificationId < 0) {
            return markBroadcastRead(tenantId, user.id(), -notificationId);
        }
        SysUserNotificationEntity entity = notificationMapper.selectOne(visibleMineWrapper(tenantId, user.id(), null)
                .eq(SysUserNotificationEntity::getId, notificationId)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("NOT_FOUND", "站内通知不存在");
        }
        if (entity.getReadAt() == null) {
            entity.setReadAt(TimeSupport.now());
            notificationMapper.updateById(entity);
        }
        return NotificationView.from(entity);
    }

    @Transactional
    public long markAllRead() {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform(user.tenantId());
        Instant now = TimeSupport.now();
        long directChanged = notificationMapper.update(null, visibleMineUpdateWrapper(tenantId, user.id(), now)
                .isNull(SysUserNotificationEntity::getReadAt)
                .set(SysUserNotificationEntity::getReadAt, now));
        long broadcastChanged = noticeReadStatusMapper.markAllUnreadBroadcastsRead(
                tenantId,
                user.id(),
                now,
                SecuritySupport.currentOperator());
        return directChanged + broadcastChanged;
    }

    @Transactional
    public long clearReadNotifications() {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform(user.tenantId());
        // 物理硬删除当前用户所有已读通知，释放存储空间。
        long directDeleted = notificationMapper.hardDeleteReadNotifications(tenantId, user.id());
        long broadcastsCleared = noticeReadStatusMapper.clearReadBroadcasts(
                tenantId,
                user.id(),
                TimeSupport.now(),
                SecuritySupport.currentOperator());
        return directDeleted + broadcastsCleared;
    }

    private NotificationView markBroadcastRead(String tenantId, Long userId, Long noticeId) {
        Instant now = TimeSupport.now();
        NoticeBroadcastProjection notice = noticeReadStatusMapper.findVisibleBroadcast(tenantId, userId, noticeId, now);
        if (notice == null) {
            throw new BusinessException("NOT_FOUND", "站内通知不存在");
        }
        if (notice.getReadAt() == null) {
            noticeReadStatusMapper.markBroadcastRead(
                    tenantId,
                    noticeId,
                    userId,
                    now,
                    SecuritySupport.currentOperator());
            notice.setReadAt(now);
        }
        return NotificationView.fromBroadcast(notice);
    }

    private LambdaQueryWrapper<SysUserNotificationEntity> visibleMineWrapper(String tenantId, Long userId, Boolean read) {
        Instant now = TimeSupport.now();
        LambdaQueryWrapper<SysUserNotificationEntity> wrapper = baseMineWrapper(tenantId, userId)
                .and(query -> query.isNull(SysUserNotificationEntity::getExpiresAt)
                        .or()
                        .gt(SysUserNotificationEntity::getExpiresAt, now));
        if (read != null) {
            if (Boolean.TRUE.equals(read)) {
                wrapper.isNotNull(SysUserNotificationEntity::getReadAt);
            } else {
                wrapper.isNull(SysUserNotificationEntity::getReadAt);
            }
        }
        return wrapper;
    }

    private LambdaUpdateWrapper<SysUserNotificationEntity> visibleMineUpdateWrapper(String tenantId, Long userId, Instant now) {
        return new LambdaUpdateWrapper<SysUserNotificationEntity>()
                .eq(SysUserNotificationEntity::getTenantId, tenantId)
                .eq(SysUserNotificationEntity::getRecipientUserId, userId)
                .eq(SysUserNotificationEntity::getDeleted, 0)
                .and(wrapper -> wrapper.isNull(SysUserNotificationEntity::getExpiresAt)
                        .or()
                        .gt(SysUserNotificationEntity::getExpiresAt, now));
    }

    private LambdaQueryWrapper<SysUserNotificationEntity> baseMineWrapper(String tenantId, Long userId) {
        return new LambdaQueryWrapper<SysUserNotificationEntity>()
                .eq(SysUserNotificationEntity::getTenantId, tenantId)
                .eq(SysUserNotificationEntity::getRecipientUserId, userId)
                .eq(SysUserNotificationEntity::getDeleted, 0);
    }

}
