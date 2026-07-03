package com.enterprise.auth.platform.modules.notification.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.notification.infrastructure.entity.SysUserNotificationEntity;
import com.enterprise.auth.platform.modules.notification.infrastructure.mapper.SysUserNotificationMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NotificationInboxService {

    private final SysUserNotificationMapper notificationMapper;
    private final CurrentUserService currentUserService;

    public NotificationInboxService(
            SysUserNotificationMapper notificationMapper,
            CurrentUserService currentUserService
    ) {
        this.notificationMapper = notificationMapper;
        this.currentUserService = currentUserService;
    }

    public PageResult<NotificationView> myNotifications(int page, int size, Boolean read) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<SysUserNotificationEntity> countWrapper = visibleMineWrapper(tenantId, user.id(), read);
        long total = notificationMapper.selectCount(countWrapper);
        int offset = (safePage - 1) * safeSize;
        List<NotificationView> records = notificationMapper.selectList(visibleMineWrapper(tenantId, user.id(), read)
                        .orderByAsc(SysUserNotificationEntity::getReadAt)
                        .orderByDesc(SysUserNotificationEntity::getCreatedAt)
                        .orderByDesc(SysUserNotificationEntity::getId)
                        .last("limit " + offset + "," + safeSize))
                .stream()
                .map(NotificationView::from)
                .toList();
        return PageResult.of(total, safePage, safeSize, records);
    }

    public long unreadCount() {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        return notificationMapper.selectCount(visibleMineWrapper(tenantId, user.id(), false));
    }

    @Transactional
    public NotificationView markRead(Long notificationId) {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
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
        String tenantId = currentTenantId(user);
        Instant now = TimeSupport.now();
        return notificationMapper.update(null, visibleMineUpdateWrapper(tenantId, user.id(), now)
                .isNull(SysUserNotificationEntity::getReadAt)
                .set(SysUserNotificationEntity::getReadAt, now));
    }

    @Transactional
    public long clearReadNotifications() {
        UserAccount user = currentUserService.requireCurrentUser();
        String tenantId = currentTenantId(user);
        // 物理硬删除当前用户所有已读通知，释放存储空间。
        return notificationMapper.hardDeleteReadNotifications(tenantId, user.id());
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

    private String currentTenantId(UserAccount user) {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : user.tenantId();
    }
}