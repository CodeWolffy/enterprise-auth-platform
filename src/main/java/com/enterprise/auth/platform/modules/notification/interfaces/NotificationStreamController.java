package com.enterprise.auth.platform.modules.notification.interfaces;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.notification.application.NotificationSseRegistry;
import com.enterprise.auth.platform.modules.notification.application.NotificationStreamTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 站内通知 SSE 流端点。
 * <p>
 * 由于浏览器原生 EventSource 无法携带自定义 Authorization 头，这里通过短期一次性
 * {@code ticket} 建立订阅，避免长期登录令牌进入 URL。该路径需在 SaToken 拦截器中排除。
 */
@Tag(name = "通知流")
@RestController
@RequestMapping("/api/notifications")
public class NotificationStreamController {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamController.class);

    private final NotificationSseRegistry sseRegistry;
    private final NotificationStreamTicketService ticketService;

    public NotificationStreamController(NotificationSseRegistry sseRegistry, NotificationStreamTicketService ticketService) {
        this.sseRegistry = sseRegistry;
        this.ticketService = ticketService;
    }

    @Operation(summary = "订阅站内通知 SSE 流")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
            @Parameter(description = "短期一次性订阅凭证") @RequestParam(name = "ticket", required = false) String ticket
    ) {
        NotificationStreamTicketService.StreamTicket streamTicket;
        try {
            streamTicket = ticketService.consume(ticket);
        } catch (BusinessException ex) {
            log.debug("站内通知 SSE 订阅凭证被拒绝。code={}，message={}", ex.code(), ex.getMessage());
            return sseError(ex);
        }
        Long userId = streamTicket.userId();
        String tenantId = streamTicket.tenantId();

        SseEmitter emitter = sseRegistry.register(tenantId, userId);
        try {
            // 立即发送一次连接确认事件，便于前端感知订阅成功。
            emitter.send(SseEmitter.event().name("open").data("connected"));
        } catch (Exception ex) {
            log.debug("站内通知 SSE 初始连接事件发送失败。userId={}", userId, ex);
        }
        log.debug("站内通知 SSE 流已注册。tenant={}，user={}，active={}", tenantId, userId, sseRegistry.activeConnectionCount());
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

    private ResponseEntity<SseEmitter> sseError(BusinessException exception) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"code\":\"" + escapeJson(exception.code()) + "\",\"message\":\"" + escapeJson(exception.getMessage()) + "\"}"));
            emitter.complete();
        } catch (Exception ex) {
            log.debug("站内通知 SSE 错误事件写入失败。code={}", exception.code(), ex);
            emitter.completeWithError(ex);
        }
        return ResponseEntity.status(exception.status())
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

}
