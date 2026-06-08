package com.enterprise.auth.platform.modules.notification.interfaces;

import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.notification.application.NotificationInboxService;
import com.enterprise.auth.platform.modules.notification.application.NotificationView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/notifications", "/api/account/notifications"})
public class NotificationController {

    private final NotificationInboxService notificationInboxService;

    public NotificationController(NotificationInboxService notificationInboxService) {
        this.notificationInboxService = notificationInboxService;
    }

    @GetMapping
    public ApiResponse<PageResult<NotificationView>> myNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean read
    ) {
        return ApiResponse.ok(notificationInboxService.myNotifications(page, size, read));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.ok(notificationInboxService.unreadCount());
    }

    @PutMapping("/{notificationId}/read")
    public ApiResponse<NotificationView> markRead(@PathVariable Long notificationId) {
        return ApiResponse.ok(notificationInboxService.markRead(notificationId));
    }

    @PutMapping("/read-all")
    public ApiResponse<Long> markAllRead() {
        return ApiResponse.ok(notificationInboxService.markAllRead());
    }
}