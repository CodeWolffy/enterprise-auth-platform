package com.enterprise.auth.platform.modules.system.application;

import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

@Component
public class TransactionalMailSupport {

    private static final String PRODUCT_NAME = "企业认证平台";

    public void send(JavaMailSender sender, String from, String to, String subject, MailContent content) throws Exception {
        MimeMessage mimeMessage = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content.textBody(), content.htmlBody());
        sender.send(mimeMessage);
    }

    public MailContent passwordResetContent(String username, String resetLink, long ttlMinutes) {
        String effectiveUsername = StringUtils.hasText(username) ? username.trim() : "用户";
        String effectiveLink = StringUtils.hasText(resetLink) ? resetLink.trim() : "";
        String ttlText = ttlMinutes + " 分钟";
        return render(new MailModel(
                "密码重置链接 " + ttlText + " 内有效，且仅可使用一次。",
                "密码重置确认",
                effectiveUsername + "，我们收到了你的密码重置请求。请在 " + ttlText + " 内完成操作。",
                "立即重置密码",
                effectiveLink,
                List.of(
                        "该链接仅可使用一次，提交新密码后会立即失效。",
                        "若链接失效，请重新发起密码重置申请。",
                        "若非你本人操作，请忽略本邮件并尽快检查账号安全。"
                ),
                List.of(
                        new DetailItem("有效时间", ttlText),
                        new DetailItem("使用限制", "一次性使用"),
                        new DetailItem("备用链接", effectiveLink)
                ),
                "为降低泄露风险，请勿转发、截图或复制给他人。"
        ));
    }

    public MailContent testMailContent(String tenantId, String channelTenantId, String host, Integer port, String from) {
        String effectiveTenantId = defaultText(tenantId, "platform");
        String effectiveChannelTenantId = defaultText(channelTenantId, "platform");
        String endpoint = defaultText(host, "unknown") + (port == null ? "" : ":" + port);
        String effectiveFrom = defaultText(from, "unknown");
        return render(new MailModel(
                "这是一封测试邮件，用于验证 SMTP 渠道是否可正常发信。",
                "邮件渠道测试",
                "如果你收到了这封邮件，说明当前渠道已经可以正常建立连接并提交邮件发送请求。",
                null,
                null,
                List.of(
                        "该邮件仅用于验证发信链路，不会触发任何业务动作。",
                        "若邮件内容和样式展示正常，说明 HTML 事务邮件能力可用。",
                        "建议继续验证密码重置等真实业务邮件是否符合预期。"
                ),
                List.of(
                        new DetailItem("请求租户", effectiveTenantId),
                        new DetailItem("实际发信通道", effectiveChannelTenantId),
                        new DetailItem("SMTP 节点", endpoint),
                        new DetailItem("发件地址", effectiveFrom)
                ),
                "如果你并未主动测试邮件渠道，可忽略本邮件。"
        ));
    }

    private MailContent render(MailModel model) {
        return new MailContent(renderText(model), renderHtml(model));
    }

    private String renderText(MailModel model) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add(PRODUCT_NAME + " - " + model.title());
        joiner.add("");
        joiner.add(model.intro());
        joiner.add("");
        if (StringUtils.hasText(model.actionUrl())) {
            if (StringUtils.hasText(model.actionLabel())) {
                joiner.add(model.actionLabel() + "：" + model.actionUrl());
            } else {
                joiner.add("链接：" + model.actionUrl());
            }
            joiner.add("");
        }
        if (!model.highlights().isEmpty()) {
            joiner.add("提示：");
            for (String item : model.highlights()) {
                joiner.add("- " + item);
            }
            joiner.add("");
        }
        if (!model.details().isEmpty()) {
            joiner.add("详情：");
            for (DetailItem detail : model.details()) {
                joiner.add(detail.label() + "：" + detail.value());
            }
            joiner.add("");
        }
        if (StringUtils.hasText(model.footer())) {
            joiner.add(model.footer());
        }
        return joiner.toString();
    }

    private String renderHtml(MailModel model) {
        String highlights = model.highlights().stream()
                .filter(StringUtils::hasText)
                .map(item -> "<li style=\"margin:0 0 8px;color:#334155;line-height:1.7;\">" + escape(item) + "</li>")
                .reduce("", String::concat);

        String details = model.details().stream()
                .filter(detail -> StringUtils.hasText(detail.label()) && StringUtils.hasText(detail.value()))
                .map(detail -> """
                        <tr>
                          <td style=\"padding:10px 0;color:#64748b;font-size:13px;border-bottom:1px solid #e2e8f0;white-space:nowrap;\">%s</td>
                          <td style=\"padding:10px 0;color:#0f172a;font-size:13px;border-bottom:1px solid #e2e8f0;word-break:break-all;\">%s</td>
                        </tr>
                        """.formatted(escape(detail.label()), escape(detail.value())))
                .reduce("", String::concat);

        String actionBlock = "";
        if (StringUtils.hasText(model.actionLabel()) && StringUtils.hasText(model.actionUrl())) {
            actionBlock = """
                    <div style=\"margin:28px 0 20px;\">
                      <a href=\"%s\" style=\"display:inline-block;padding:12px 22px;background:#2563eb;color:#ffffff;text-decoration:none;border-radius:10px;font-weight:600;\">%s</a>
                    </div>
                    <div style=\"padding:14px 16px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;color:#475569;font-size:13px;line-height:1.7;word-break:break-all;\">
                      若按钮无法点击，请复制以下链接到浏览器打开：<br>
                      <a href=\"%s\" style=\"color:#2563eb;text-decoration:none;\">%s</a>
                    </div>
                    """.formatted(escapeAttribute(model.actionUrl()), escape(model.actionLabel()), escapeAttribute(model.actionUrl()), escape(model.actionUrl()));
        }

        String footer = StringUtils.hasText(model.footer())
                ? "<div style=\"margin-top:20px;color:#64748b;font-size:12px;line-height:1.7;\">" + escape(model.footer()) + "</div>"
                : "";

        return """
                <!DOCTYPE html>
                <html lang=\"zh-CN\">
                  <body style=\"margin:0;padding:24px;background:#f1f5f9;font-family:'Segoe UI',Arial,'PingFang SC','Microsoft YaHei',sans-serif;\">
                    <div style=\"max-width:680px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:18px;overflow:hidden;\">
                      <div style=\"padding:18px 24px;background:#0f172a;color:#e2e8f0;font-size:12px;letter-spacing:.08em;\">%s</div>
                      <div style=\"padding:32px 24px 28px;\">
                        <div style=\"display:inline-block;padding:6px 10px;background:#dbeafe;color:#1d4ed8;border-radius:999px;font-size:12px;font-weight:600;\">事务通知</div>
                        <h1 style=\"margin:18px 0 12px;color:#0f172a;font-size:26px;line-height:1.3;\">%s</h1>
                        <p style=\"margin:0;color:#334155;font-size:15px;line-height:1.8;\">%s</p>
                        %s
                        <div style=\"margin-top:24px;padding:18px 20px;background:#fff7ed;border:1px solid #fed7aa;border-radius:14px;\">
                          <div style=\"margin-bottom:10px;color:#9a3412;font-size:13px;font-weight:700;\">重要提示</div>
                          <ul style=\"margin:0;padding-left:20px;\">%s</ul>
                        </div>
                        <div style=\"margin-top:24px;padding:20px 22px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:14px;\">
                          <div style=\"margin-bottom:12px;color:#0f172a;font-size:14px;font-weight:700;\">明细</div>
                          <table role=\"presentation\" style=\"width:100%%;border-collapse:collapse;\">%s</table>
                        </div>
                        %s
                      </div>
                    </div>
                  </body>
                </html>
                """.formatted(
                escape(model.preheader()),
                escape(model.title()),
                escape(model.intro()),
                actionBlock,
                highlights,
                details,
                footer
        );
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(Objects.toString(value, ""));
    }

    private String escapeAttribute(String value) {
        return escape(value).replace("\"", "&quot;");
    }

    public record MailContent(String textBody, String htmlBody) {
    }

    private record MailModel(
            String preheader,
            String title,
            String intro,
            String actionLabel,
            String actionUrl,
            List<String> highlights,
            List<DetailItem> details,
            String footer
    ) {
    }

    private record DetailItem(String label, String value) {
    }
}