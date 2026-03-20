package com.enterprise.auth.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.persistence.entity.SysTenantEntity;
import com.enterprise.auth.platform.persistence.mapper.SysTenantMapper;
import com.enterprise.auth.platform.tenant.TenantProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
public class LoginPageController {

    private static final Map<String, ScopeDescriptor> SCOPE_DESCRIPTORS = Map.of(
            "openid", new ScopeDescriptor("身份标识", "允许客户端确认当前登录主体的唯一身份。"),
            "profile", new ScopeDescriptor("基础资料", "允许读取当前账号的角色、菜单和基础资料快照。"),
            "api.read", new ScopeDescriptor("接口读取", "允许访问平台内需要读取权限的业务接口。"),
            "api.write", new ScopeDescriptor("接口写入", "允许访问平台内需要写入权限的业务接口。")
    );

    private final SysTenantMapper sysTenantMapper;
    private final TenantProperties tenantProperties;
    private final RegisteredClientRepository registeredClientRepository;

    public LoginPageController(
            @Nullable SysTenantMapper sysTenantMapper,
            TenantProperties tenantProperties,
            RegisteredClientRepository registeredClientRepository
    ) {
        this.sysTenantMapper = sysTenantMapper;
        this.tenantProperties = tenantProperties;
        this.registeredClientRepository = registeredClientRepository;
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String loginPage(
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "error", required = false) String error
    ) {
        TenantOption currentTenant = currentTenant(tenantId);
        RegisteredClient client = findClient(clientId);
        String clientDisplayName = resolveClientDisplayName(client, clientId);
        String errorHtml = error == null
                ? ""
                : """
                        <div class="alert">
                          用户名、密码或租户错误，请检查后重新登录。
                        </div>
                        """;
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>统一认证登录</title>
                  <style>
                    :root{
                      --brand:%s;
                      --brand-soft:%s;
                      --text:#1f2937;
                      --muted:#667085;
                      --line:rgba(15,23,42,.09);
                      --card:rgba(255,255,255,.88);
                    }
                    *{box-sizing:border-box}
                    body{
                      margin:0;
                      min-height:100vh;
                      font-family:"Microsoft YaHei","PingFang SC",sans-serif;
                      color:var(--text);
                      background:
                        radial-gradient(circle at top left,var(--brand-soft),transparent 30%%),
                        linear-gradient(140deg,#f5f8ff 0%%,#f7f2ea 100%%);
                      display:grid;
                      place-items:center;
                      padding:24px;
                    }
                    .shell{
                      width:min(1120px,100%%);
                      display:grid;
                      grid-template-columns:1.15fr .85fr;
                      background:var(--card);
                      border:1px solid var(--line);
                      border-radius:28px;
                      overflow:hidden;
                      box-shadow:0 32px 96px rgba(15,23,42,.14);
                      backdrop-filter:blur(18px);
                    }
                    .hero{
                      padding:40px;
                      background:
                        radial-gradient(circle at top right,rgba(255,255,255,.85),transparent 28%%),
                        linear-gradient(145deg,var(--brand-soft),rgba(255,255,255,.55));
                      display:grid;
                      gap:24px;
                    }
                    .hero h1,.panel h2{margin:0}
                    .hero h1{font-size:40px;line-height:1.1}
                    .hero p,.panel p,.meta,.tenant-list small{color:var(--muted)}
                    .eyebrow{
                      display:inline-flex;
                      letter-spacing:.18em;
                      text-transform:uppercase;
                      color:var(--brand);
                      font-size:12px;
                      font-weight:700;
                    }
                    .client-card,.tenant-list,.highlights,.panel{
                      background:rgba(255,255,255,.72);
                      border:1px solid rgba(255,255,255,.65);
                      border-radius:22px;
                    }
                    .client-card,.tenant-list{padding:20px}
                    .client-card strong{display:block;font-size:22px;margin-bottom:8px}
                    .highlights{padding:0;margin:0;list-style:none;display:grid}
                    .highlights li{
                      padding:16px 20px;
                      border-top:1px solid rgba(15,23,42,.06);
                    }
                    .highlights li:first-child{border-top:none}
                    .tenant-list{display:grid;gap:12px}
                    .tenant-item{
                      display:flex;
                      justify-content:space-between;
                      align-items:center;
                      padding:14px 16px;
                      border-radius:16px;
                      background:rgba(255,255,255,.76);
                    }
                    .tenant-badge{
                      display:inline-flex;
                      align-items:center;
                      gap:8px;
                      padding:6px 10px;
                      border-radius:999px;
                      background:rgba(255,255,255,.8);
                      color:var(--brand);
                      font-size:12px;
                      font-weight:700;
                    }
                    .panel{
                      padding:40px;
                      display:grid;
                      gap:18px;
                    }
                    form{display:grid;gap:14px}
                    label{display:grid;gap:8px;font-size:14px;font-weight:700}
                    input,select{
                      width:100%%;
                      border:1px solid #d0d5dd;
                      border-radius:16px;
                      padding:14px 16px;
                      font-size:14px;
                      background:#fff;
                    }
                    button{
                      border:none;
                      border-radius:16px;
                      padding:14px 18px;
                      background:var(--brand);
                      color:#fff;
                      font-size:15px;
                      font-weight:700;
                      cursor:pointer;
                    }
                    .meta{
                      display:flex;
                      justify-content:space-between;
                      gap:16px;
                      padding:14px 16px;
                      border-radius:16px;
                      background:rgba(15,23,42,.03);
                    }
                    .alert{
                      padding:14px 16px;
                      border-radius:16px;
                      color:#b42318;
                      background:#fef3f2;
                      border:1px solid #fecdca;
                    }
                    @media (max-width: 920px){
                      .shell{grid-template-columns:1fr}
                      .hero h1{font-size:32px}
                    }
                  </style>
                </head>
                <body>
                  <div class="shell">
                    <section class="hero">
                      <div>
                        <span class="eyebrow">Spring Authorization Server</span>
                        <h1>%s 统一认证中心</h1>
                        <p>当前登录页已经接入多租户授权流程，登录后会继续完成 OAuth2 授权确认与令牌签发。</p>
                      </div>

                      <div class="client-card">
                        <span class="tenant-badge">%s</span>
                        <strong>%s</strong>
                        <div class="meta">
                          <span>租户：%s</span>
                          <span>客户端：%s</span>
                        </div>
                      </div>

                      <ul class="highlights">
                        <li>支持租户切换、授权码模式、刷新令牌与 PKCE。</li>
                        <li>当前登录会话会自动继承租户上下文，用于后续菜单、数据权限与审计链路。</li>
                        <li>如果客户端要求授权确认，登录完成后会自动进入中文同意页。</li>
                      </ul>

                      <div class="tenant-list">
                        <strong>可登录租户</strong>
                        %s
                      </div>
                    </section>

                    <section class="panel">
                      <div>
                        <span class="eyebrow">登录</span>
                        <h2>进入授权流程</h2>
                        <p>请确认租户后再输入账号密码，避免登录到错误的隔离空间。</p>
                      </div>
                      %s
                      <form method="post" action="/login">
                        <label for="tenantId">
                          租户
                          <select id="tenantId" name="tenantId">%s</select>
                        </label>
                        <label for="username">
                          用户名
                          <input id="username" name="username" type="text" autocomplete="username" placeholder="请输入用户名" />
                        </label>
                        <label for="password">
                          密码
                          <input id="password" name="password" type="password" autocomplete="current-password" placeholder="请输入密码" />
                        </label>
                        <button type="submit">登录并继续授权</button>
                      </form>
                    </section>
                  </div>
                </body>
                </html>
                """.formatted(
                currentTenant.brandColor(),
                currentTenant.brandSoftColor(),
                HtmlUtils.htmlEscape(currentTenant.tenantName()),
                currentTenant.platformLevel() ? "平台级租户" : "业务租户",
                HtmlUtils.htmlEscape(clientDisplayName),
                HtmlUtils.htmlEscape(currentTenant.tenantName()),
                HtmlUtils.htmlEscape(StringUtils.hasText(clientId) ? clientId : "未指定"),
                tenantCardsHtml(),
                errorHtml,
                tenantOptionsHtml(currentTenant.tenantId())
        );
    }

    @GetMapping(value = "/oauth2/consent", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String consentPage(HttpServletRequest request) {
        String clientId = request.getParameter("client_id");
        String state = request.getParameter("state");
        TenantOption currentTenant = currentTenant(request.getParameter("tenantId"));
        RegisteredClient client = findClient(clientId);
        String clientName = resolveClientDisplayName(client, clientId);
        List<String> scopes = resolveScopes(request);
        String scopeHtml = scopes.isEmpty()
                ? """
                        <div class="scope-card">
                          <strong>未声明作用域</strong>
                          <p>当前授权请求没有提交可识别的 scope，默认按基础访问处理。</p>
                        </div>
                        """
                : scopes.stream().map(this::scopeCardHtml).collect(Collectors.joining());
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>授权确认</title>
                  <style>
                    :root{
                      --brand:%s;
                      --brand-soft:%s;
                      --text:#1f2937;
                      --muted:#667085;
                      --line:rgba(15,23,42,.08);
                    }
                    *{box-sizing:border-box}
                    body{
                      margin:0;
                      min-height:100vh;
                      font-family:"Microsoft YaHei","PingFang SC",sans-serif;
                      color:var(--text);
                      background:
                        radial-gradient(circle at top left,var(--brand-soft),transparent 30%%),
                        linear-gradient(135deg,#f5f8ff 0%%,#f8f1e8 100%%);
                      display:grid;
                      place-items:center;
                      padding:24px;
                    }
                    .panel{
                      width:min(860px,100%%);
                      display:grid;
                      gap:20px;
                      padding:34px;
                      border-radius:28px;
                      background:rgba(255,255,255,.92);
                      border:1px solid var(--line);
                      box-shadow:0 32px 96px rgba(15,23,42,.14);
                    }
                    .eyebrow{
                      display:inline-flex;
                      letter-spacing:.18em;
                      text-transform:uppercase;
                      color:var(--brand);
                      font-size:12px;
                      font-weight:700;
                    }
                    h1{margin:0;font-size:32px}
                    .summary{
                      display:grid;
                      grid-template-columns:repeat(3,minmax(0,1fr));
                      gap:14px;
                    }
                    .summary-card,.scope-card{
                      padding:18px;
                      border-radius:20px;
                      border:1px solid var(--line);
                      background:rgba(248,250,252,.85);
                    }
                    .summary-card strong,.scope-card strong{display:block;margin-bottom:8px}
                    .scope-grid{display:grid;gap:14px}
                    .scope-card p,.summary-card p{margin:0;color:var(--muted);line-height:1.7}
                    .scope-card label{display:flex;align-items:flex-start;gap:12px}
                    .actions{display:flex;gap:14px}
                    button{
                      flex:1;
                      border:none;
                      border-radius:16px;
                      padding:14px 18px;
                      font-size:15px;
                      font-weight:700;
                      cursor:pointer;
                    }
                    .approve{background:var(--brand);color:#fff}
                    .deny{background:#eef2f6;color:#1f2937}
                    @media (max-width: 820px){
                      .summary{grid-template-columns:1fr}
                    }
                  </style>
                </head>
                <body>
                  <div class="panel">
                    <div>
                      <span class="eyebrow">授权确认</span>
                      <h1>确认客户端访问权限</h1>
                      <p style="color:var(--muted);line-height:1.8;">
                        客户端 <strong>%s</strong> 正在申请访问 <strong>%s</strong> 租户的数据与接口，请确认要授予的作用域。
                      </p>
                    </div>

                    <div class="summary">
                      <div class="summary-card">
                        <strong>租户上下文</strong>
                        <p>%s</p>
                      </div>
                      <div class="summary-card">
                        <strong>客户端编号</strong>
                        <p>%s</p>
                      </div>
                      <div class="summary-card">
                        <strong>授权方式</strong>
                        <p>%s</p>
                      </div>
                    </div>

                    <form method="post" action="/oauth2/authorize">
                      <input type="hidden" name="client_id" value="%s" />
                      <input type="hidden" name="state" value="%s" />
                      <input type="hidden" name="tenantId" value="%s" />
                      <div class="scope-grid">%s</div>
                      <div class="actions" style="margin-top:18px;">
                        <button class="approve" type="submit">确认授权</button>
                        <button class="deny" type="submit" name="consent_action" value="deny">拒绝</button>
                      </div>
                    </form>
                  </div>
                </body>
                </html>
                """.formatted(
                currentTenant.brandColor(),
                currentTenant.brandSoftColor(),
                HtmlUtils.htmlEscape(clientName),
                HtmlUtils.htmlEscape(currentTenant.tenantName()),
                HtmlUtils.htmlEscape(currentTenant.platformLevel() ? "平台级统一租户" : "业务租户"),
                HtmlUtils.htmlEscape(StringUtils.hasText(clientId) ? clientId : "未指定"),
                HtmlUtils.htmlEscape(describeClientMode(client)),
                HtmlUtils.htmlEscape(StringUtils.hasText(clientId) ? clientId : ""),
                HtmlUtils.htmlEscape(state == null ? "" : state),
                HtmlUtils.htmlEscape(currentTenant.tenantId()),
                scopeHtml
        );
    }

    private String resolveClientDisplayName(@Nullable RegisteredClient client, @Nullable String clientId) {
        if (client != null && StringUtils.hasText(client.getClientName())) {
            return client.getClientName();
        }
        if (StringUtils.hasText(clientId)) {
            return clientId;
        }
        return "企业权限平台前端";
    }

    private String tenantCardsHtml() {
        return tenantOptions().stream()
                .map(option -> """
                        <div class="tenant-item">
                          <div>
                            <strong>%s</strong>
                            <small>%s</small>
                          </div>
                          <span class="tenant-badge">%s</span>
                        </div>
                        """.formatted(
                        HtmlUtils.htmlEscape(option.tenantName()),
                        HtmlUtils.htmlEscape(option.tenantId()),
                        option.platformLevel() ? "平台级" : "业务"
                ))
                .collect(Collectors.joining());
    }

    private String tenantOptionsHtml(String currentTenantId) {
        StringBuilder options = new StringBuilder();
        for (TenantOption option : tenantOptions()) {
            options.append("<option value=\"")
                    .append(HtmlUtils.htmlEscape(option.tenantId()))
                    .append("\"")
                    .append(option.tenantId().equals(currentTenantId) ? " selected" : "")
                    .append(">")
                    .append(HtmlUtils.htmlEscape(option.tenantName()))
                    .append(" (").append(HtmlUtils.htmlEscape(option.tenantId())).append(")")
                    .append("</option>");
        }
        return options.toString();
    }

    private String scopeCardHtml(String scope) {
        ScopeDescriptor descriptor = SCOPE_DESCRIPTORS.getOrDefault(
                scope.toLowerCase(Locale.ROOT),
                new ScopeDescriptor(scope, "该作用域由客户端自定义声明，请按业务需要确认。")
        );
        return """
                <div class="scope-card">
                  <label>
                    <input type="checkbox" name="scope" value="%s" checked />
                    <span>
                      <strong>%s</strong>
                      <p>%s</p>
                    </span>
                  </label>
                </div>
                """.formatted(
                HtmlUtils.htmlEscape(scope),
                HtmlUtils.htmlEscape(descriptor.title()),
                HtmlUtils.htmlEscape(descriptor.description())
        );
    }

    private List<String> resolveScopes(HttpServletRequest request) {
        String[] scopes = request.getParameterValues("scope");
        if (scopes == null || scopes.length == 0) {
            return List.of();
        }
        return Arrays.stream(scopes)
                .flatMap(value -> Arrays.stream(value.split(" ")))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    private RegisteredClient findClient(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return null;
        }
        return registeredClientRepository.findByClientId(clientId);
    }

    private String describeClientMode(@Nullable RegisteredClient client) {
        if (client == null) {
            return "未知客户端";
        }
        if (client.getClientAuthenticationMethods().stream().anyMatch(method -> "none".equals(method.getValue()))) {
            return "公共客户端 / PKCE";
        }
        return "机密客户端";
    }

    private TenantOption currentTenant(String tenantId) {
        String resolvedTenantId = StringUtils.hasText(tenantId) ? tenantId : tenantProperties.platformTenantId();
        return tenantOptions().stream()
                .filter(item -> item.tenantId().equals(resolvedTenantId))
                .findFirst()
                .orElse(new TenantOption(resolvedTenantId, resolvedTenantId, false));
    }

    private List<TenantOption> tenantOptions() {
        if (sysTenantMapper == null) {
            return List.of(new TenantOption(tenantProperties.platformTenantId(), "平台租户", true));
        }
        List<SysTenantEntity> tenants = sysTenantMapper.selectList(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0)
                .eq(SysTenantEntity::getTenantStatus, 1)
                .orderByDesc(SysTenantEntity::getPlatformLevel)
                .orderByAsc(SysTenantEntity::getTenantId));
        if (tenants.isEmpty()) {
            return List.of(new TenantOption(tenantProperties.platformTenantId(), "平台租户", true));
        }
        return tenants.stream()
                .map(tenant -> new TenantOption(
                        tenant.getTenantId(),
                        tenant.getTenantName(),
                        tenant.getPlatformLevel() != null && tenant.getPlatformLevel() == 1
                ))
                .toList();
    }

    private record TenantOption(String tenantId, String tenantName, boolean platformLevel) {
        private String brandColor() {
            return platformLevel ? "#0f766e" : "#9a3412";
        }

        private String brandSoftColor() {
            return platformLevel ? "rgba(15,118,110,.18)" : "rgba(154,52,18,.16)";
        }
    }

    private record ScopeDescriptor(String title, String description) {
    }
}
