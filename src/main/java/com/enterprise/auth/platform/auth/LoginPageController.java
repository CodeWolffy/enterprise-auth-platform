package com.enterprise.auth.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.auth.dto.CsrfTokenResponse;
import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.config.FrontendProperties;
import com.enterprise.auth.platform.persistence.entity.SysConfigEntity;
import com.enterprise.auth.platform.persistence.entity.SysOauthScopeEntity;
import com.enterprise.auth.platform.persistence.entity.SysTenantEntity;
import com.enterprise.auth.platform.persistence.mapper.SysConfigMapper;
import com.enterprise.auth.platform.persistence.mapper.SysOauthScopeMapper;
import com.enterprise.auth.platform.persistence.mapper.SysTenantMapper;
import com.enterprise.auth.platform.tenant.TenantProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class LoginPageController {

    private static final Pattern HEX_COLOR_PATTERN =
            Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");
    private static final Pattern RGB_COLOR_PATTERN = Pattern.compile(
            "^rgba?\\(\\s*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])\\s*,\\s*"
                    + "(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])\\s*,\\s*"
                    + "(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])"
                    + "(?:\\s*,\\s*(?:0(?:\\.\\d+)?|1(?:\\.0+)?|\\.\\d+)\\s*)?\\)$"
    );

    private static final Map<String, String> DEFAULT_SCOPE_DESCRIPTIONS = Map.of(
            "openid", "读取用户基础身份信息，用于建立统一登录会话。",
            "profile", "读取用户资料信息，用于展示昵称、头像等基础资料。",
            "api.read", "接口读取：允许读取平台接口与管理数据。",
            "api.write", "接口写入：允许创建、修改或删除平台业务数据。"
    );

    private final SysTenantMapper sysTenantMapper;
    private final SysConfigMapper sysConfigMapper;
    private final SysOauthScopeMapper sysOauthScopeMapper;
    private final FrontendProperties frontendProperties;
    private final TenantProperties tenantProperties;
    private final RegisteredClientRepository registeredClientRepository;

    public LoginPageController(
            @Nullable SysTenantMapper sysTenantMapper,
            @Nullable SysConfigMapper sysConfigMapper,
            @Nullable SysOauthScopeMapper sysOauthScopeMapper,
            FrontendProperties frontendProperties,
            TenantProperties tenantProperties,
            RegisteredClientRepository registeredClientRepository
    ) {
        this.sysTenantMapper = sysTenantMapper;
        this.sysConfigMapper = sysConfigMapper;
        this.sysOauthScopeMapper = sysOauthScopeMapper;
        this.frontendProperties = frontendProperties;
        this.tenantProperties = tenantProperties;
        this.registeredClientRepository = registeredClientRepository;
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String loginPage(
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "error", required = false) String error,
            HttpServletRequest request
    ) {
        return browserRedirectHtml(requireFrontendLoginUrl(request));
    }

    @GetMapping(value = "/oauth2/consent", params = "format=json")
    public ApiResponse<ConsentContextResponse> consentContext(HttpServletRequest request) {
        String clientId = request.getParameter("client_id");
        String state = request.getParameter("state");
        TenantOption currentTenant = currentTenant(request.getParameter("tenantId"));
        RegisteredClient client = findClient(clientId);
        String clientName = resolveClientDisplayName(client, clientId);

        Map<String, String> descriptions = resolveScopeDescriptions(currentTenant.tenantId());
        List<ConsentScopeResponse> scopeItems = resolveScopes(request).stream()
                .map(scope -> {
                    String key = normalizeScope(scope);
                    String desc = descriptions.getOrDefault(key, "该作用域由客户端自定义声明，请按业务需要确认。");
                    return new ConsentScopeResponse(scope, desc);
                })
                .toList();

        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute("_csrf");
        }
        CsrfTokenResponse csrf = new CsrfTokenResponse(
                csrfToken == null ? "" : csrfToken.getHeaderName(),
                csrfToken == null ? "_csrf" : csrfToken.getParameterName(),
                csrfToken == null ? "" : csrfToken.getToken()
        );

        return ApiResponse.ok(new ConsentContextResponse(
                StringUtils.hasText(clientId) ? clientId : "",
                clientName,
                currentTenant.tenantId(),
                currentTenant.tenantName(),
                currentTenant.platformLevel() ? "平台级统一租户" : "业务租户",
                describeClientMode(client),
                StringUtils.hasText(state) ? state : "",
                scopeItems,
                csrf
        ));
    }

    @GetMapping(value = "/oauth2/consent", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String consentPage(HttpServletRequest request) {
        return browserRedirectHtml(requireFrontendUiUrl(request, "/auth/consent"));
    }

    private String buildFrontendUiUrl(HttpServletRequest request, String path) {
        String frontendOrigin = frontendProperties.resolvedAllowedOrigins().stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (!StringUtils.hasText(frontendOrigin)) {
            return null;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(frontendOrigin).path(path);
        request.getParameterMap().forEach((key, values) -> {
            if (values == null || values.length == 0) {
                return;
            }
            for (String value : values) {
                builder.queryParam(key, value);
            }
        });
        return builder.build(true).toUriString();
    }

    private String requireFrontendUiUrl(HttpServletRequest request, String path) {
        String target = buildFrontendUiUrl(request, path);
        if (!StringUtils.hasText(target)) {
            throw new IllegalStateException("未配置前端可用地址 app.frontend.allowed-origins，无法完成认证页面跳转");
        }
        return target;
    }

    private String requireFrontendLoginUrl(HttpServletRequest request) {
        String loginUrl = requireFrontendUiUrl(request, "/login");
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(loginUrl);
        appendAuthorizeContextFromSavedRequest(builder, request);
        return builder.build(true).toUriString();
    }

    private void appendAuthorizeContextFromSavedRequest(UriComponentsBuilder builder, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        Object savedRequest = session.getAttribute("SPRING_SECURITY_SAVED_REQUEST");
        if (!(savedRequest instanceof DefaultSavedRequest defaultSavedRequest)) {
            return;
        }

        String redirectUrl = defaultSavedRequest.getRedirectUrl();
        if (!StringUtils.hasText(redirectUrl)) {
            return;
        }
        UriComponentsBuilder savedBuilder = UriComponentsBuilder.fromUriString(redirectUrl);
        Map<String, List<String>> savedParams = savedBuilder.build(true).getQueryParams();
        if (savedParams.isEmpty()) {
            return;
        }

        Set<String> keys = Set.of(
                "response_type",
                "client_id",
                "redirect_uri",
                "scope",
                "state",
                "code_challenge",
                "code_challenge_method",
                "tenantId"
        );
        for (String key : keys) {
            if (!savedParams.containsKey(key)) {
                continue;
            }
            for (String value : savedParams.get(key)) {
                builder.queryParam(key, value);
            }
        }
    }

    private String browserRedirectHtml(String targetUrl) {
        String safeUrl = escape(targetUrl);
        return """
                <!DOCTYPE html>
                <html lang=\"zh-CN\">
                <head>
                  <meta charset=\"UTF-8\" />
                  <meta http-equiv=\"refresh\" content=\"0;url=%s\" />
                  <title>跳转中</title>
                </head>
                <body>
                  正在跳转到前端授权确认页，如果未自动跳转请点击：<a href=\"%s\">继续</a>
                </body>
                </html>
                """.formatted(safeUrl, safeUrl);
    }

    public record ConsentScopeResponse(
            String scopeCode,
            String scopeDesc
    ) {
    }

    public record ConsentContextResponse(
            String clientId,
            String clientName,
            String tenantId,
            String tenantName,
            String tenantLevel,
            String clientMode,
            String state,
            List<ConsentScopeResponse> scopes,
            CsrfTokenResponse csrf
    ) {
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

    private Map<String, String> resolveScopeDescriptions(String tenantId) {
        Map<String, String> descriptions = new LinkedHashMap<>(DEFAULT_SCOPE_DESCRIPTIONS);
        if (sysOauthScopeMapper == null) {
            return descriptions;
        }
        sysOauthScopeMapper.selectList(new LambdaQueryWrapper<SysOauthScopeEntity>()
                        .in(SysOauthScopeEntity::getTenantId, Arrays.asList("platform", tenantId))
                        .eq(SysOauthScopeEntity::getDeleted, 0)
                        .eq(SysOauthScopeEntity::getEnabled, 1)
                        .eq(SysOauthScopeEntity::getVisibleInConsent, 1))
                .forEach(item -> descriptions.put(normalizeScope(item.getScopeCode()), item.getScopeDesc()));
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
        return org.springframework.web.util.HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private static String sanitizeCssColor(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String candidate = value.trim();
        if (HEX_COLOR_PATTERN.matcher(candidate).matches() || RGB_COLOR_PATTERN.matcher(candidate).matches()) {
            return candidate;
        }
        return fallback;
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
            String fallback = platformLevel ? "#0f766e" : "#9a3412";
            return sanitizeCssColor(brandColor, fallback);
        }

        String brandSoftColor() {
            String fallback = platformLevel ? "rgba(15,118,110,.18)" : "rgba(154,52,18,.16)";
            return sanitizeCssColor(brandSoftColor, fallback);
        }
    }
}
