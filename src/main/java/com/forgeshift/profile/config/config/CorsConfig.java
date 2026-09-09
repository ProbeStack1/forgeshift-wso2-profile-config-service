package com.forgeshift.profile.config.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS as a plain {@link CorsFilter} bean named {@code corsFilter} - the exact shape
 * ps-community-svc uses, kept identical on purpose.
 *
 * <p>Spring Security's {@code CorsConfigurer} fetches the bean with that name, casts
 * it to {@code CorsFilter}, and runs it inside the security chain - which Boot applies
 * to error dispatches as well as requests, so every response carries CORS headers and
 * a preflight never reaches the token check.</p>
 *
 * <p>This used to be a {@code FilterRegistrationBean<CorsFilter>} under the same
 * name, registered at {@code HIGHEST_PRECEDENCE}. That works only while there is no
 * Spring Security on the classpath: the moment there is, the cast above throws and
 * the context dies at startup. Discovery and assessment each crash-looped four
 * deploys on exactly that before being changed to this. Do not put the wrapper
 * back.</p>
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
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(methods);
        config.setAllowedHeaders(headers);
        config.setExposedHeaders(exposed);
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
