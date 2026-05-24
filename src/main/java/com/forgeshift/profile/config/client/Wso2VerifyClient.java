package com.forgeshift.profile.config.client;

import com.forgeshift.profile.config.config.ProfileConfigProperties;
import com.forgeshift.profile.config.dto.Wso2VerifyRequest;
import com.forgeshift.profile.config.dto.Wso2VerifyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Calls a WSO2 instance directly to verify credentials work. Never returns
 * the full token - only a 6-char prefix so the operator can visually confirm.
 */
@Slf4j
@Component
public class Wso2VerifyClient {

    private final WebClient webClient;
    private final ProfileConfigProperties props;

    public Wso2VerifyClient(@Qualifier("wso2VerifyWebClient") WebClient webClient,
                            ProfileConfigProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    public Wso2VerifyResponse verify(Wso2VerifyRequest req) {
        long start = System.currentTimeMillis();
        try {
            String base = trimTrailingSlash(req.getWso2BaseUrl());
            String token = acquireToken(base, req);
            if (token == null) {
                return Wso2VerifyResponse.builder()
                        .success(false)
                        .tokenAuthType("NONE")
                        .elapsedMs(System.currentTimeMillis() - start)
                        .errorMessage("WSO2 /oauth2/token returned no access_token. " +
                                "Check client_id/client_secret and that password grant is enabled for the client.")
                        .build();
            }

            String tenantInfo = null;
            boolean fetchTenant = Boolean.TRUE.equals(req.getIncludeTenantInfo())
                    || props.getVerify().isWso2IncludeTenantInfo();
            if (fetchTenant) {
                tenantInfo = fetchTenantInfo(base, token);
            }

            return Wso2VerifyResponse.builder()
                    .success(true)
                    .tokenAuthType("BEARER")
                    .tokenPrefix(token.length() > 6 ? token.substring(0, 6) + "..." : "***")
                    .tenantInfo(tenantInfo)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .verifiedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            log.warn("WSO2 verify failed: {}", e.getMessage());
            return Wso2VerifyResponse.builder()
                    .success(false)
                    .tokenAuthType("NONE")
                    .elapsedMs(System.currentTimeMillis() - start)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    private String acquireToken(String baseUrl, Wso2VerifyRequest req) {
        String basic = Base64.getEncoder().encodeToString(
                (req.getClientId() + ":" + req.getClientSecret()).getBytes());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("username", req.getUsername());
        form.add("password", req.getPassword());
        form.add("scope", "apim:api_view apim:admin apim:subscribe");

        try {
            Map<String, Object> body = webClient.post()
                    .uri(baseUrl + "/oauth2/token")
                    .header("Authorization", "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(props.getVerify().getTimeoutSeconds()))
                    .block();
            Object t = body != null ? body.get("access_token") : null;
            return t == null ? null : t.toString();
        } catch (WebClientResponseException e) {
            throw new IllegalStateException(
                    "WSO2 /oauth2/token returned " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        }
    }

    private String fetchTenantInfo(String baseUrl, String token) {
        try {
            Object body = webClient.get()
                    .uri(baseUrl + "/api/am/admin/v4/tenant-info/admin")
                    .header("Authorization", "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(props.getVerify().getTimeoutSeconds()))
                    .block();
            return body == null ? null : body.toString();
        } catch (Exception e) {
            log.debug("tenant-info probe failed (non-fatal): {}", e.getMessage());
            return null;
        }
    }

    private static String trimTrailingSlash(String s) {
        if (!StringUtils.hasText(s)) return s;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
