package com.forgeshift.profile.config.config;

import com.forge.security.authn.security.ForgeAuthnAuthenticationFilter;
import com.forge.security.authn.validator.AuthnValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

/**
 * Verifies the platform session token on every request that is not explicitly public.
 *
 * <p>Until this arrived the service had no authentication at all, and it is the one
 * that stores every WSO2, Kong Konnect and cloud-storage credential the migration
 * uses: anyone who could reach it could read or replace them, under any tenant they
 * cared to name in {@code X-Partner-Id}. Identity now comes from a signature this
 * service checks against the platform JWKS — see {@link AuthenticatedActorResolver}
 * for reading it.</p>
 *
 * <p>Two filters, in order. {@link CookieBearerTokenFilter} turns the browser's
 * {@code ps_auth_token} cookie into the bearer header the library expects, then
 * {@code ForgeAuthnAuthenticationFilter} validates it. Both are also registered with
 * a DISABLED {@link FilterRegistrationBean}: any {@code Filter} exposed as a bean is
 * otherwise auto-registered by Boot as a plain servlet filter as well, so it would run
 * twice — once here in the chain and once outside it, where a rejection would not be
 * handled by the entry point. ({@code TenantInterceptor} is such a plain servlet
 * filter, deliberately: it only reads a header, and it runs whether or not the
 * request goes on to be rejected here.)</p>
 *
 * <p>Everything is switchable with {@code forge.authn.enabled}. It defaults to ON
 * (matching ps-community-svc, so a service cannot end up unauthenticated by leaving a
 * property out); the disabled chain below exists so a local run or a rollback can turn
 * it off without removing the dependency.</p>
 *
 * <p>CORS is wired exactly as ps-community-svc wires it: {@link CorsConfig} publishes
 * a plain {@code CorsFilter} bean named {@code corsFilter}, and both chains below call
 * {@code http.cors(...)}, so Spring Security's {@code CorsConfigurer} picks that bean
 * up by name and runs it inside the chain - every response, error dispatches included,
 * carries CORS headers, and a preflight never needs a token. The name and the type
 * both matter: the configurer is applied to every {@code HttpSecurity} whether or not
 * {@code http.cors(...)} is called, and its first act is to fetch the bean named
 * {@code corsFilter} cast to {@code CorsFilter}. Discovery and assessment each
 * crash-looped four deploys with a {@code FilterRegistrationBean} under that name.</p>
 */
@Configuration(proxyBeanMethods = false)
public class AuthenticationSecurityConfig {

    /**
     * Open endpoints, matched AFTER the context path is stripped.
     *
     * <p>Health has to stay open for the GKE probes, and the API docs are how the
     * team reads the service. Nothing here returns a profile or a credential.</p>
     */
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    @ConditionalOnProperty(prefix = "forge.authn", name = "enabled", havingValue = "true", matchIfMissing = true)
    ForgeAuthnAuthenticationFilter forgeAuthnAuthenticationFilter(
            AuthnValidator validator, AuthenticationEntryPoint entryPoint) {
        AntPathMatcher matcher = new AntPathMatcher();
        return new ForgeAuthnAuthenticationFilter(validator, entryPoint) {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;
                String path = pathWithinApplication(request);
                for (String pattern : PUBLIC_PATHS) if (matcher.match(pattern, path)) return true;
                return false;
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "forge.authn", name = "enabled", havingValue = "true", matchIfMissing = true)
    FilterRegistrationBean<ForgeAuthnAuthenticationFilter> disableStandaloneAuthFilterRegistration(
            ForgeAuthnAuthenticationFilter filter) {
        FilterRegistrationBean<ForgeAuthnAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "forge.authn", name = "enabled", havingValue = "true", matchIfMissing = true)
    CookieBearerTokenFilter cookieBearerTokenFilter(@Value("${forge.authn.jwks-uri:}") String jwksUri) {
        // Fail at startup rather than 401 every request at runtime: without a JWKS
        // there is no key to verify against, and that is a deployment mistake.
        if (!StringUtils.hasText(jwksUri)) {
            throw new IllegalStateException("forge.authn.jwks-uri must not be blank when authentication is enabled");
        }
        return new CookieBearerTokenFilter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "forge.authn", name = "enabled", havingValue = "true", matchIfMissing = true)
    FilterRegistrationBean<CookieBearerTokenFilter> disableStandaloneCookieFilterRegistration(
            CookieBearerTokenFilter filter) {
        FilterRegistrationBean<CookieBearerTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "forge.authn", name = "enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain authenticationSecurityFilterChain(
            HttpSecurity http, CookieBearerTokenFilter cookieFilter,
            ForgeAuthnAuthenticationFilter authFilter, AuthenticationEntryPoint entryPoint,
            AccessDeniedHandler deniedHandler) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // No session is ever created: the token is the whole of the state.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(cookieFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(authFilter, CookieBearerTokenFilter.class);
        return http.build();
    }

    /** Everything open. The escape hatch for a local run or a rollback. */
    @Bean
    @ConditionalOnProperty(prefix = "forge.authn", name = "enabled", havingValue = "false")
    SecurityFilterChain disabledAuthenticationSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable).cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    /**
     * The path the patterns above are written against.
     *
     * <p>This service is served under {@code /wso2/config/v1}, so the raw URI
     * carries that prefix and {@code /actuator/health} would never match it.</p>
     */
    private static String pathWithinApplication(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        return StringUtils.hasText(contextPath) && path.startsWith(contextPath)
                ? path.substring(contextPath.length()) : path;
    }
}
