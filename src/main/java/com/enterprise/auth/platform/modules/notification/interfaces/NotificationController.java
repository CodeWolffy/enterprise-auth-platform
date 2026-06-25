package com.enterprise.auth.platform.modules.notification.interfaces;

import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.notification.application.NotificationInboxService;
import com.enterprise.auth.platform.modules.notification.application.NotificationStreamTicketService;
import com.enterprise.auth.platform.modules.notification.application.NotificationView;
import com.enterprise.auth.platform.modules.log.infrastructure.annotation.SysLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "站内通知")
@RestController
@RequestMapping({"/api/notifications", "/api/account/notifications"})
public class NotificationController {

    private final NotificationInboxService notificationInboxService;
    private final NotificationStreamTicketService notificationStreamTicketService;

    public NotificationController(
            NotificationInboxService notificationInboxService,
            NotificationStreamTicketService notificationStreamTicketService
    ) {
        this.notificationInboxService = notificationInboxService;
        this.notificationStreamTicketService = notificationStreamTicketService;
    }

    @Operation(summary = "创建站内通知 SSE 短期订阅凭证")
    @PostMapping("/stream-ticket")
    public ApiResponse<NotificationStreamTicketService.StreamTicketResponse> streamTicket() {
        return ApiResponse.ok(notificationStreamTicketService.issue());
    }

    @Operation(summary = "分页查询当前用户通知")
    @GetMapping
    public ApiResponse<PageResult<NotificationView>> myNotifications(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "是否已读") @RequestParam(required = false) Boolean read
    ) {
        return ApiResponse.ok(notificationInboxService.myNotifications(page, size, read));
    }

    @Operation(summary = "获取当前用户未读通知数量")
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.ok(notificationInboxService.unreadCount());
    }

    @SysLog("标记单条通知为已读")
    @Operation(summary = "标记单条通知为已读")
    @PutMapping("/{notificationId}/read")
    public ApiResponse<NotificationView> markRead(
            @Parameter(description = "通知ID") @PathVariable Long notificationId) {
        return ApiResponse.ok(notificationInboxService.markRead(notificationId));
    }

    @SysLog("标记当前用户所有通知为已读")
    @Operation(summary = "标记当前用户所有通知为已读")
    @PutMapping("/read-all")
    public ApiResponse<Long> markAllRead() {
        return ApiResponse.ok(notificationInboxService.markAllRead());
    }

    @SysLog("清空当前用户已读通知")
    @Operation(summary = "清空当前用户已读通知")
    @DeleteMapping("/read")
    public ApiResponse<Long> clearRead() {
        return ApiResponse.ok(notificationInboxService.clearReadNotifications());
    }
}