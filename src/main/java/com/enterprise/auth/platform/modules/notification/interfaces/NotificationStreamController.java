package com.enterprise.auth.platform.modules.notification.interfaces;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.notification.application.NotificationSseRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 站内通知 SSE 流端点。
 * <p>
 * 由于浏览器原生 EventSource 无法携带自定义 Authorization 头，这里通过 query 参数
 * {@code token} 传递会话令牌，并手动校验。该路径需在 SaToken 拦截器中排除。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationStreamController {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamController.class);

    private final NotificationSseRegistry sseRegistry;

    public NotificationStreamController(NotificationSseRegistry sseRegistry) {
        this.sseRegistry = sseRegistry;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam(name = "token", required = false) String token,
            HttpServletResponse response
    ) {
        if (!StringUtils.hasText(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            throw new BusinessException("UNAUTHORIZED", "缺少站内通知订阅凭证");
        }
        Object loginId;
        try {
            loginId = StpUtil.stpLogic.getLoginIdByToken(token);
        } catch (Exception ex) {
            loginId = null;
        }
        if (loginId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            throw new BusinessException("UNAUTHORIZED", "站内通知订阅凭证已失效");
        }
        Long userId = Long.parseLong(String.valueOf(loginId));
        String tenantId = resolveTenantId(token, userId);

        SseEmitter emitter = sseRegistry.register(tenantId, userId);
        try {
            // 立即发送一次连接确认事件，便于前端感知订阅成功。
            emitter.send(SseEmitter.event().name("open").data("connected"));
        } catch (Exception ex) {
            log.debug("Failed to send initial SSE open event for user {}", userId, ex);
        }
        log.debug("SSE stream registered: tenant={}, user={}, active={}", tenantId, userId, sseRegistry.activeConnectionCount());
        return emitter;
    }

    private String resolveTenantId(String token, Long userId) {
        try {
            SaSession tokenSession = StpUtil.getTokenSessionByToken(token);
            Object activeTenantId = tokenSession.get("activeTenantId");
            if (activeTenantId != null && StringUtils.hasText(String.valueOf(activeTenantId))) {
                return String.valueOf(activeTenantId);
            }
            Object tenantId = tokenSession.get("tenantId");
            if (tenantId != null && StringUtils.hasText(String.valueOf(tenantId))) {
                return String.valueOf(tenantId);
            }
        } catch (Exception ignored) {
            // 兜底使用默认租户标识，避免订阅失败。
        }
        return "platform";
    }
}
