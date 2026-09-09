package com.forgeshift.profile.config.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Adapts the browser auth cookie to the bearer-header contract used by forge-auth-lib.
 *
 * <p>The platform signs you in on probestack.io, which sets {@code ps_auth_token} —
 * an RS256 context JWT — as an httpOnly cookie scoped to {@code .probestack.io}. The
 * browser therefore sends it to every host under that domain on its own, and the UI's
 * nginx forwards it here. forge-auth-lib only reads {@code Authorization: Bearer},
 * so this filter presents the cookie under that header rather than teaching the
 * library a second way in.</p>
 *
 * <p>A request wrapper, not a mutated request: servlet headers are immutable, and
 * overriding the three accessors is the supported way to add one. The value is
 * rejected if it carries CR or LF, which would otherwise let a crafted cookie
 * inject a second header.</p>
 *
 * <p>Ported from ps-community-svc, which is the reference implementation of this
 * pattern across the platform. Keep the two in step.</p>
 */
final class CookieBearerTokenFilter extends OncePerRequestFilter {
    static final String AUTH_COOKIE_NAME = "ps_auth_token";
    private static final Logger log = LoggerFactory.getLogger(CookieBearerTokenFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = cookieToken(request);
        if (token == null) {
            // Not an error here: a caller may legitimately send the bearer header
            // itself, and the authentication filter downstream decides either way.
            log.debug("authSource=cookie|event=notFound|cookieName={}|authorizationHeaderPresent={}|method={}|path={}",
                    AUTH_COOKIE_NAME, request.getHeader(HttpHeaders.AUTHORIZATION) != null,
                    request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String bearerValue = token.startsWith("Bearer ") ? token : "Bearer " + token;
        log.debug("authSource=cookie|event=adaptedToBearer|cookieName={}|method={}|path={}",
                AUTH_COOKIE_NAME, request.getMethod(), request.getRequestURI());
        filterChain.doFilter(new AuthorizationHeaderRequest(request, bearerValue), response);
        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            log.warn("authSource=cookie|event=rejected|cookieName={}|method={}|path={}",
                    AUTH_COOKIE_NAME, request.getMethod(), request.getRequestURI());
        }
    }

    private String cookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (!AUTH_COOKIE_NAME.equals(cookie.getName())) continue;
            String value = cookie.getValue();
            if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) return null;
            return value.trim();
        }
        return null;
    }

    private static final class AuthorizationHeaderRequest extends HttpServletRequestWrapper {
        private final String authorization;

        private AuthorizationHeaderRequest(HttpServletRequest request, String authorization) {
            super(request);
            this.authorization = authorization;
        }

        @Override
        public String getHeader(String name) {
            return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name) ? authorization : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)
                    ? Collections.enumeration(Collections.singletonList(authorization))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            Enumeration<String> existing = super.getHeaderNames();
            if (existing != null) existing.asIterator().forEachRemaining(names::add);
            names.add(HttpHeaders.AUTHORIZATION);
            return Collections.enumeration(names);
        }
    }
}
