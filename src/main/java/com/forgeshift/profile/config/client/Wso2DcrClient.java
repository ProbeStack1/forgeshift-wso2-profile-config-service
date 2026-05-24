package com.forgeshift.profile.config.client;

import com.forgeshift.profile.config.config.ProfileConfigProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Dynamic Client Registration against a WSO2 API Manager instance.
 * Hits {@code POST /client-registration/v0.17/register} with admin Basic auth
 * and returns the generated {@code clientId}/{@code clientSecret}.
 *
 * If a DCR application already exists for the same {@code clientName}, WSO2
 * returns the existing credentials — so calling this is idempotent per name.
 */
@Slf4j
@Component
public class Wso2DcrClient {

    private static final String DCR_PATH = "/client-registration/v0.17/register";

    private final WebClient webClient;
    private final ProfileConfigProperties props;

    public Wso2DcrClient(@Qualifier("wso2VerifyWebClient") WebClient webClient,
                         ProfileConfigProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    public DcrCredentials register(DcrRequest req) {
        String base = trimTrailingSlash(req.getWso2BaseUrl());
        String basic = Base64.getEncoder().encodeToString(
                (req.getUsername() + ":" + req.getPassword()).getBytes());

        Map<String, Object> body = Map.of(
                "callbackUrl", "www.google.lk",
                "clientName", req.getClientName(),
                "owner", req.getUsername(),
                "grantType", "password refresh_token client_credentials",
                "saasApp", true
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = webClient.post()
                    .uri(base + DCR_PATH)
                    .header("Authorization", "Basic " + basic)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(props.getVerify().getTimeoutSeconds()))
                    .block();

            if (resp == null) {
                throw new IllegalStateException("WSO2 DCR returned empty response");
            }
            String clientId = asString(resp.get("clientId"));
            String clientSecret = asString(resp.get("clientSecret"));
            if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
                throw new IllegalStateException(
                        "WSO2 DCR response missing clientId/clientSecret: " + resp);
            }
            return DcrCredentials.builder()
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .build();
        } catch (WebClientResponseException e) {
            throw new IllegalStateException(
                    "WSO2 DCR call to " + base + DCR_PATH + " returned "
                            + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        }
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static String trimTrailingSlash(String s) {
        if (!StringUtils.hasText(s)) return s;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DcrRequest {
        private String wso2BaseUrl;
        private String username;
        private String password;
        private String clientName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DcrCredentials {
        private String clientId;
        private String clientSecret;
    }
}
