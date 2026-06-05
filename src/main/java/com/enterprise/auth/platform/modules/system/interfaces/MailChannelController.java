package com.enterprise.auth.platform.modules.system.interfaces;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.RateLimit;
import com.enterprise.auth.platform.modules.system.application.MailChannelApplicationService;
import com.enterprise.auth.platform.modules.system.application.MailChannelPreset;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "邮件渠道管理")
@RestController
@RequestMapping("/api/system/mail-channel")
public class MailChannelController {

    private final MailChannelApplicationService mailChannelService;

    public MailChannelController(MailChannelApplicationService mailChannelService) {
        this.mailChannelService = mailChannelService;
    }

    @Operation(summary = "查询邮件渠道预设列表")
    @GetMapping("/presets")
    @SaCheckPermission(PermissionCodes.SYSTEM_READ)
    public ApiResponse<List<Map<String, Object>>> presets() {
        List<Map<String, Object>> list = Arrays.stream(MailChannelPreset.values())
                .map(preset -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("code", preset.name());
                    map.put("host", preset.host());
                    map.put("port", preset.port());
                    map.put("protocol", preset.protocol());
                    map.put("useSsl", preset.useSsl());
                    map.put("useStartTls", preset.useStartTls());
                    return map;
                })
                .toList();
        return ApiResponse.ok(list);
    }

    @Operation(summary = "查询当前租户的邮件渠道配置")
    @GetMapping
    @SaCheckPermission(PermissionCodes.SYSTEM_READ)
    public ApiResponse<MailChannelResponse> getChannel() {
        return ApiResponse.ok(mailChannelService.getVisibleChannel(currentTenant()).orElse(null));
    }

    @Operation(summary = "保存或更新当前租户的邮件渠道配置")
    @PostMapping
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<MailChannelResponse> saveChannel(@Valid @RequestBody MailChannelRequest request) {
        return ApiResponse.ok(mailChannelService.saveOrUpdate(request));
    }

    @Operation(summary = "删除当前租户的邮件渠道配置")
    @DeleteMapping
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<Void> deleteChannel() {
        mailChannelService.deleteChannel();
        return ApiResponse.ok();
    }

    @Operation(summary = "发送测试邮件")
    @RateLimit(key = "mail-channel-test", strategy = RateLimit.Strategy.USER_AND_IP, capacity = 3, refillTokens = 3, refillDurationSeconds = 60)
    @PostMapping("/test")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<Map<String, Object>> testSend(
            @Parameter(description = "接收测试邮件的邮箱地址") @RequestParam @NotBlank @Email String toEmail
    ) {
        mailChannelService.sendTestMail(currentTenant(), toEmail);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "测试邮件已发送到 " + toEmail);
        return ApiResponse.ok(result);
    }

    private String currentTenant() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }
}