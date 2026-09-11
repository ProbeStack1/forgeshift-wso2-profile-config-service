package com.forgeshift.profile.config.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * forge-logging-lib writes request and response bodies to the log, and masks a JSON field only
 * when its name is on {@code forge.logging.mask-request-keys} / {@code mask-response-keys}
 * exactly - {@code secret} does not cover {@code clientSecret}. The request and response classes
 * are where this service's credentials travel, so a credential field added to one of them would
 * be logged in clear until someone remembered the lists. This test notices first.
 */
class RequestLogMaskingTest {

    /** Names that hold a credential: password, *secret, *pat, *token, apiKey, privateKey, the SA JSON. */
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)password|.*secret|.*pat|.*token|apikey|privatekey|serviceaccountjson(base64)?");

    /** Every shape the API reads or writes: the DTOs, and the documents the Kong and Git APIs return as-is. */
    private static final String[] API_PACKAGES = {
            "com.forgeshift.profile.config.dto",
            "com.forgeshift.profile.config.domain"
    };

    @Test
    void everyCredentialField_isMaskedInRequestAndResponseLogs() {
        Properties config = applicationYaml();
        Set<String> credentialFields = credentialFields();

        // Guards the scan itself - an empty set would pass everything below.
        assertThat(credentialFields)
                .contains("password", "clientsecret", "konnectpat", "pat", "serviceaccountjsonbase64");
        assertThat(keys(config, "forge.logging.mask-request-keys")).containsAll(credentialFields);
        assertThat(keys(config, "forge.logging.mask-response-keys")).containsAll(credentialFields);
    }

    @Test
    void sessionCookieAndBearerHeader_areMasked() {
        assertThat(keys(applicationYaml(), "forge.logging.mask-header-keys"))
                .contains("cookie", "authorization");
    }

    private static Set<String> credentialFields() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
        Set<String> names = new TreeSet<>();
        for (String apiPackage : API_PACKAGES) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(apiPackage)) {
                for (Class<?> type = load(candidate.getBeanClassName());
                     type != null && type != Object.class;
                     type = type.getSuperclass()) {
                    for (Field field : type.getDeclaredFields()) {
                        if (CREDENTIAL.matcher(field.getName()).matches()) {
                            names.add(field.getName().toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        }
        return names;
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Properties applicationYaml() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        return yaml.getObject();
    }

    /** The library matches keys case-insensitively, so compare them that way. */
    private static Set<String> keys(Properties config, String property) {
        return Arrays.stream(config.getProperty(property, "").split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .map(key -> key.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
