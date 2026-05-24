package com.forgeshift.profile.config.filter;

import com.forgeshift.profile.config.config.ProfileConfigProperties;
import com.forgeshift.profile.config.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolves the tenant from the {@code X-Partner-Id} header (or the
 * configured fallback) and stows it on the thread-local {@link TenantContext}
 * for the duration of the request.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class TenantInterceptor extends OncePerRequestFilter {

    private final ProfileConfigProperties props;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = props.getTenant().getHeaderName();
        String tenant = req.getHeader(header);
        if (!StringUtils.hasText(tenant)) {
            tenant = props.getTenant().getDefaultTenant();
        }
        try {
            TenantContext.set(tenant);
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }
}
