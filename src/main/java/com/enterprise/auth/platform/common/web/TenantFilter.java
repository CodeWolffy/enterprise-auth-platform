package com.enterprise.auth.platform.common.web;

import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.context.RequestContext;
import com.enterprise.auth.platform.common.context.RequestContextCleaner;
import com.enterprise.auth.platform.common.context.RequestLogContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private final ClientIpResolver clientIpResolver;
    private final RequestContextCleaner requestContextCleaner;

    public TenantFilter(ClientIpResolver clientIpResolver, RequestContextCleaner requestContextCleaner) {
        this.clientIpResolver = clientIpResolver;
        this.requestContextCleaner = requestContextCleaner;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = RequestIdSupport.resolveIncoming(request.getHeader(RequestIdSupport.HEADER));

        String clientIp = clientIpResolver.resolve(request);
        RequestContext.setRequestId(requestId);
        RequestContext.setClientIp(clientIp);
        RequestContext.setStartTime(System.currentTimeMillis());
        request.setAttribute(RequestIdSupport.HEADER, requestId);
        RequestLogContext.bindRequest(requestId, clientIp, request.getMethod(), request.getRequestURI());
        response.setHeader(RequestIdSupport.HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            requestContextCleaner.clear();
            TenantContext.clear();
            RequestContext.clear();
            RequestLogContext.clearRequest();
        }
    }
}
