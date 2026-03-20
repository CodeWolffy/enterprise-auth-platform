package com.enterprise.auth.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.web.HtmlTemplateRenderer;
import com.enterprise.auth.platform.persistence.entity.SysConfigEntity;
import com.enterprise.auth.platform.persistence.entity.SysDictEntity;
import com.enterprise.auth.platform.persistence.entity.SysTenantEntity;
import com.enterprise.auth.platform.persistence.mapper.SysConfigMapper;
import com.enterprise.auth.platform.persistence.mapper.SysDictMapper;
import com.enterprise.auth.platform.persistence.mapper.SysTenantMapper;
import com.enterprise.auth.platform.tenant.TenantProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
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

    private static final Map<String, String> DEFAULT_SCOPE_DESCRIPTIONS = Map.of(
            "openid", "读取用户基础身份信息，用于建立统一登录会话。",
            "profile", "读取用户资料信息，用于展示昵称、头像等基础资料。",
            "api.read", "接口读取：允许读取平台接口与管理数据。",
            "api.write", "接口写入：允许创建、修改或删除平台业务数据。"
    );

    private final SysTenantMapper sysTenantMapper;
    private final SysConfigMapper sysConfigMapper;
    private final SysDictMapper sysDictMapper;
    private final TenantProperties tenantProperties;
    private final RegisteredClientRepository registeredClientRepository;
    private final HtmlTemplateRenderer htmlTemplateRenderer;

    public LoginPageController(
            @Nullable SysTenantMapper sysTenantMapper,
            @Nullable SysConfigMapper sysConfigMapper,
            @Nullable SysDictMapper sysDictMapper,
            TenantProperties tenantProperties,
            RegisteredClientRepository registeredClientRepository,
            HtmlTemplateRenderer htmlTemplateRenderer
    ) {
        this.sysTenantMapper = sysTenantMapper;
        this.sysConfigMapper = sysConfigMapper;
        this.sysDictMapper = sysDictMapper;
        this.tenantProperties = tenantProperties;
        this.registeredClientRepository = registeredClientRepository;
        this.htmlTemplateRenderer = htmlTemplateRenderer;
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

        Map<String, String> model = new LinkedHashMap<>();
        model.put("brandColor", currentTenant.brandColor());
        model.put("brandSoftColor", currentTenant.brandSoftColor());
        model.put("tenantName", escape(currentTenant.tenantName()));
        model.put("tenantLevel", currentTenant.platformLevel() ? "平台级租户" : "业务租户");
        model.put("clientDisplayName", escape(clientDisplayName));
        model.put("clientId", escape(StringUtils.hasText(clientId) ? clientId : "未指定"));
        model.put("tenantCardsHtml", tenantCardsHtml());
        model.put("tenantOptionsHtml", tenantOptionsHtml(currentTenant.tenantId()));
        model.put("errorHtml", error == null ? "" : """
                <div class="alert">
                  用户名、密码或租户信息错误，请检查后重新登录。
                </div>
                """);
        return htmlTemplateRenderer.render("templates/oauth-login.html", model);
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
        Map<String, String> scopeDescriptions = resolveScopeDescriptions(currentTenant.tenantId());

        String scopeHtml = scopes.isEmpty()
                ? """
                        <div class="scope-card">
                          <strong>未声明作用域</strong>
                          <p>当前授权请求没有提交可识别的 scope，默认按基础访问处理。</p>
                        </div>
                        """
                : scopes.stream().map(scope -> scopeCardHtml(scope, scopeDescriptions)).collect(Collectors.joining());

        Map<String, String> model = new LinkedHashMap<>();
        model.put("brandColor", currentTenant.brandColor());
        model.put("brandSoftColor", currentTenant.brandSoftColor());
        model.put("clientName", escape(clientName));
        model.put("tenantName", escape(currentTenant.tenantName()));
        model.put("tenantLevel", escape(currentTenant.platformLevel() ? "平台级统一租户" : "业务租户"));
        model.put("clientId", escape(StringUtils.hasText(clientId) ? clientId : "未指定"));
        model.put("clientIdRaw", escape(StringUtils.hasText(clientId) ? clientId : ""));
        model.put("clientMode", escape(describeClientMode(client)));
        model.put("state", escape(state == null ? "" : state));
        model.put("tenantId", escape(currentTenant.tenantId()));
        model.put("scopeHtml", scopeHtml);
        return htmlTemplateRenderer.render("templates/oauth-consent.html", model);
    }

    private String resolveClientDisplayName(@Nullable RegisteredClient client, @Nullable String clientId) {
        if (client != null && StringUtils.hasText(client.getClientName())) {
            return client.getClientName();
        }
        if (StringUtils.hasText(clientId)) {
            return clientId;
        }
        return "企业权限管理平台前端";
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
                        escape(option.tenantName()),
                        escape(option.tenantId()),
                        option.platformLevel() ? "平台级" : "业务"
                ))
                .collect(Collectors.joining());
    }

    private String tenantOptionsHtml(String currentTenantId) {
        StringBuilder options = new StringBuilder();
        for (TenantOption option : tenantOptions()) {
            options.append("<option value=\"")
                    .append(escape(option.tenantId()))
                    .append("\"")
                    .append(option.tenantId().equals(currentTenantId) ? " selected" : "")
                    .append(">")
                    .append(escape(option.tenantName()))
                    .append(" (").append(escape(option.tenantId())).append(")")
                    .append("</option>");
        }
        return options.toString();
    }

    private String scopeCardHtml(String scope, Map<String, String> scopeDescriptions) {
        String key = normalizeScope(scope);
        String description = scopeDescriptions.getOrDefault(key, "该作用域由客户端自定义声明，请按业务需要确认。");
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
                """.formatted(escape(scope), escape(scope), escape(description));
    }

    private Map<String, String> resolveScopeDescriptions(String tenantId) {
        Map<String, String> descriptions = new LinkedHashMap<>(DEFAULT_SCOPE_DESCRIPTIONS);
        if (sysDictMapper == null) {
            return descriptions;
        }
        sysDictMapper.selectList(new LambdaQueryWrapper<SysDictEntity>()
                        .eq(SysDictEntity::getDictType, "oauth_scope")
                        .in(SysDictEntity::getTenantId, Arrays.asList("platform", tenantId))
                        .eq(SysDictEntity::getDeleted, 0))
                .forEach(item -> descriptions.put(normalizeScope(item.getDictCode()), item.getDictValue()));
        return descriptions;
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

    private String normalizeScope(String scope) {
        return scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
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
        TenantOption option = tenantOptions().stream()
                .filter(item -> item.tenantId().equals(resolvedTenantId))
                .findFirst()
                .orElse(new TenantOption(resolvedTenantId, resolvedTenantId, false, null, null));
        if (sysConfigMapper != null) {
            sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigEntity>()
                            .eq(SysConfigEntity::getTenantId, option.tenantId())
                            .eq(SysConfigEntity::getDeleted, 0))
                    .forEach(config -> {
                        if ("brand.color".equals(config.getConfigKey())) {
                            option.overrideBrandColor(config.getConfigValue());
                        } else if ("brand.soft_color".equals(config.getConfigKey())) {
                            option.overrideBrandSoftColor(config.getConfigValue());
                        }
                    });
        }
        return option;
    }

    private List<TenantOption> tenantOptions() {
        if (sysTenantMapper == null) {
            return List.of(new TenantOption(tenantProperties.platformTenantId(), "平台租户", true, null, null));
        }
        List<SysTenantEntity> tenants = sysTenantMapper.selectList(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0)
                .eq(SysTenantEntity::getTenantStatus, 1)
                .orderByDesc(SysTenantEntity::getPlatformLevel)
                .orderByAsc(SysTenantEntity::getTenantId));
        if (tenants.isEmpty()) {
            return List.of(new TenantOption(tenantProperties.platformTenantId(), "平台租户", true, null, null));
        }
        return tenants.stream()
                .map(tenant -> new TenantOption(
                        tenant.getTenantId(),
                        tenant.getTenantName(),
                        tenant.getPlatformLevel() != null && tenant.getPlatformLevel() == 1,
                        null,
                        null
                ))
                .toList();
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private static class TenantOption {
        private final String tenantId;
        private final String tenantName;
        private final boolean platformLevel;
        private String brandColor;
        private String brandSoftColor;

        TenantOption(String tenantId, String tenantName, boolean platformLevel, String brandColor, String brandSoftColor) {
            this.tenantId = tenantId;
            this.tenantName = tenantName;
            this.platformLevel = platformLevel;
            this.brandColor = brandColor;
            this.brandSoftColor = brandSoftColor;
        }

        void overrideBrandColor(String color) {
            this.brandColor = color;
        }

        void overrideBrandSoftColor(String color) {
            this.brandSoftColor = color;
        }

        String tenantId() {
            return tenantId;
        }

        String tenantName() {
            return tenantName;
        }

        boolean platformLevel() {
            return platformLevel;
        }

        String brandColor() {
            if (StringUtils.hasText(brandColor)) {
                return brandColor;
            }
            return platformLevel ? "#0f766e" : "#9a3412";
        }

        String brandSoftColor() {
            if (StringUtils.hasText(brandSoftColor)) {
                return brandSoftColor;
            }
            return platformLevel ? "rgba(15,118,110,.18)" : "rgba(154,52,18,.16)";
        }
    }
}
