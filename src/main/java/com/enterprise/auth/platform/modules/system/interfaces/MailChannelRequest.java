package com.enterprise.auth.platform.modules.system.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "邮件渠道配置请求")
public record MailChannelRequest(
        @Schema(description = "渠道类型：QQ、NETEASE、GMAIL、OUTLOOK、CUSTOM")
        @Size(max = 32)
        String provider,

        @Schema(description = "SMTP 服务器地址", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 128)
        String mailHost,

        @Schema(description = "SMTP 端口", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Min(1)
        @Max(65535)
        Integer mailPort,

        @Schema(description = "SMTP 用户名/邮箱地址", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 128)
        String mailUsername,

        @Schema(description = "SMTP 密码或授权码（留空则不修改）")
        @Size(max = 512)
        String mailPassword,

        @Schema(description = "发件人地址", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Email
        @Size(max = 128)
        String mailFrom,

        @Schema(description = "邮件协议", defaultValue = "smtp")
        @Pattern(regexp = "(?i)^smtp$", message = "仅支持 smtp 协议")
        String mailProtocol,

        @Schema(description = "是否使用 SSL")
        boolean useSsl,

        @Schema(description = "是否使用 STARTTLS")
        boolean useStartTls,

        @Schema(description = "是否启用")
        boolean enabled
) {
}