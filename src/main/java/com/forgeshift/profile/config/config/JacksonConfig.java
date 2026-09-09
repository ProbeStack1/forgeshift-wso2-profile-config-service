package com.forgeshift.profile.config.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Keeps Spring Boot's module-aware {@link ObjectMapper} authoritative over the one
 * forge-auth-lib brings with it.
 *
 * <p>The library registers an {@code ObjectMapper} bean of its own. Boot's
 * {@code JacksonAutoConfiguration} backs off as soon as any {@code ObjectMapper} bean
 * exists, so without this class the library's mapper - no {@code JavaTimeModule}, no
 * {@code spring.jackson.*} settings - becomes the one Spring MVC serialises every
 * response with, and the first response carrying an {@code Instant} or
 * {@code LocalDateTime} dies with a 500. It was noticed on the cutover service, whose
 * {@code /ping} is the only unauthenticated route in the six that returns a timestamp;
 * every authenticated response with a date on this service would have gone the same
 * way.</p>
 *
 * <p>Built from Boot's {@link Jackson2ObjectMapperBuilder}, so the modules Boot
 * registers and every {@code spring.jackson.*} property in application.yml still
 * apply. Same class ps-community-svc added in the same commit as its authentication,
 * for the same reason.</p>
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper applicationObjectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.createXmlMapper(false)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
