package com.forgeshift.profile.config.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS via a servlet {@link CorsFilter} at highest precedence — NOT WebMvcConfigurer.
 * The MVC-level mapping only decorates handler-mapped responses, so error dispatches
 * (500s, non-handler 404s) went out WITHOUT CORS headers and the browser reported them
 * as CORS failures, masking the real error. The filter decorates every response.
 *
 * <p>Origins are applied as PATTERNS: a match echoes the exact request Origin back
 * (never a literal "*"), which works from any localhost port / IP, with or without
 * credentials.</p>
 */
@Configuration
public class CorsConfig {

    @Value("${cors.origins:*}")
    private List<String> origins;

    @Value("${cors.allowed-methods:*}")
    private List<String> methods;

    @Value("${cors.allowed-headers:*}")
    private List<String> headers;

    @Value("${cors.exposed-headers:*}")
    private List<String> exposed;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(methods);
        config.setAllowedHeaders(headers);
        config.setExposedHeaders(exposed);
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
