package com.enterprise.auth.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.persistence.entity.SysTenantEntity;
import com.enterprise.auth.platform.persistence.mapper.SysTenantMapper;
import com.enterprise.auth.platform.tenant.TenantProperties;
import java.util.List;
import org.springframework.lang.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
public class LoginPageController {

    private final SysTenantMapper sysTenantMapper;
    private final TenantProperties tenantProperties;

    public LoginPageController(@Nullable SysTenantMapper sysTenantMapper, TenantProperties tenantProperties) {
        this.sysTenantMapper = sysTenantMapper;
        this.tenantProperties = tenantProperties;
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String loginPage(
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @RequestParam(name = "error", required = false) String error
    ) {
        String currentTenant = (tenantId == null || tenantId.isBlank()) ? tenantProperties.platformTenantId() : tenantId;
        StringBuilder options = new StringBuilder();
        for (TenantOption option : tenantOptions()) {
            options.append("<option value=\"")
                    .append(HtmlUtils.htmlEscape(option.tenantId()))
                    .append("\"")
                    .append(option.tenantId().equals(currentTenant) ? " selected" : "")
                    .append(">")
                    .append(HtmlUtils.htmlEscape(option.tenantName()))
                    .append(" (").append(HtmlUtils.htmlEscape(option.tenantId())).append(")")
                    .append("</option>");
        }
        String errorHtml = error == null ? "" : "<p style=\"color:#b42318;margin:0 0 16px;\">用户名、密码或租户错误</p>";
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>企业级权限管理平台登录</title>
                  <style>
                    body{margin:0;font-family:\"Microsoft YaHei\",sans-serif;background:linear-gradient(135deg,#eef6ff,#f7f3ea);min-height:100vh;display:flex;align-items:center;justify-content:center;color:#1f2937}
                    .panel{width:min(420px,92vw);background:#fff;border-radius:20px;box-shadow:0 24px 80px rgba(15,23,42,.12);padding:32px}
                    h1{margin:0 0 8px;font-size:24px}
                    p{margin:0 0 24px;color:#64748b}
                    label{display:block;margin:0 0 8px;font-size:14px;font-weight:600}
                    input,select{width:100%;box-sizing:border-box;border:1px solid #d0d7e2;border-radius:12px;padding:12px 14px;margin:0 0 16px;font-size:14px}
                    button{width:100%;border:none;border-radius:12px;padding:12px 14px;background:#0f766e;color:#fff;font-size:15px;font-weight:700;cursor:pointer}
                  </style>
                </head>
                <body>
                  <div class="panel">
                    <h1>统一认证登录</h1>
                    <p>当前页面用于 Spring Authorization Server 标准授权流程。</p>
                    __ERROR_HTML__
                    <form method="post" action="/login">
                      <label for="tenantId">租户</label>
                      <select id="tenantId" name="tenantId">__TENANT_OPTIONS__</select>
                      <label for="username">用户名</label>
                      <input id="username" name="username" type="text" autocomplete="username" placeholder="请输入用户名" />
                      <label for="password">密码</label>
                      <input id="password" name="password" type="password" autocomplete="current-password" placeholder="请输入密码" />
                      <button type="submit">登录并授权</button>
                    </form>
                  </div>
                </body>
                </html>
                """
                .replace("__ERROR_HTML__", errorHtml)
                .replace("__TENANT_OPTIONS__", options.toString());
    }

    private List<TenantOption> tenantOptions() {
        if (sysTenantMapper == null) {
            return List.of(new TenantOption(tenantProperties.platformTenantId(), "平台租户"));
        }
        List<SysTenantEntity> tenants = sysTenantMapper.selectList(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0)
                .eq(SysTenantEntity::getTenantStatus, 1)
                .orderByAsc(SysTenantEntity::getPlatformLevel)
                .orderByAsc(SysTenantEntity::getTenantId));
        if (tenants.isEmpty()) {
            return List.of(new TenantOption(tenantProperties.platformTenantId(), "平台租户"));
        }
        return tenants.stream()
                .map(tenant -> new TenantOption(tenant.getTenantId(), tenant.getTenantName()))
                .toList();
    }

    private record TenantOption(String tenantId, String tenantName) {
    }
}
