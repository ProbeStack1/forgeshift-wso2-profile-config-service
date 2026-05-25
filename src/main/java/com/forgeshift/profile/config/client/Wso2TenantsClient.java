package com.forgeshift.profile.config.client;

import com.forgeshift.profile.config.config.ProfileConfigProperties;
import com.forgeshift.profile.config.dto.Wso2TenantsRequest;
import com.forgeshift.profile.config.dto.Wso2TenantsResponse;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Enumerates tenant domains on a WSO2 instance.
 *
 * <p>Primary path: the APIM DevPortal endpoint
 * ({@code GET /api/am/devportal/v3/tenants?state=active}) — designed for
 * publicly listing active tenants and granted under the {@code apim:subscribe}
 * scope, which the default DCR app in APIM is allowed to issue.
 *
 * <p>Fallback path: the WSO2 IS endpoint
 * ({@code GET /api/server/v1/tenants}) with Basic auth. This requires
 * {@code internal_list_tenants}, which default-DCR clients can't request,
 * so we don't even try Bearer here — Basic with super-admin credentials
 * works on some IS-only installs.
 *
 * <p>The first endpoint that returns a non-empty list wins. Raw upstream
 * bodies are logged at INFO so misconfiguration is diagnosable from logs.
 */
@Slf4j
@Component
public class Wso2TenantsClient {

    private static final String DEVPORTAL_PATH = "/api/am/devportal/v3/tenants";
    private static final String IS_PATH = "/api/server/v1/tenants";
    private static final String TOKEN_PATH = "/oauth2/token";

    /**
     * Scopes requested for the tenants token. Includes the APIM scopes the
     * default DCR app can issue ({@code openid}, {@code apim:subscribe},
     * {@code apim:admin}, {@code apim:api_view}) plus IS internal scopes
     * ({@code internal_list_tenants}) in case the install allows them — WSO2
     * ignores ones the client can't request.
     */
    private static final String TENANT_SCOPES =
            "openid apim:subscribe apim:admin apim:api_view "
                    + "apim:tenant_info_view internal_list_tenants";

    private final WebClient webClient;
    private final ProfileConfigProperties props;

    public Wso2TenantsClient(@Qualifier("wso2VerifyWebClient") WebClient webClient,
                             ProfileConfigProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    public Wso2TenantsResponse listTenants(Wso2TenantsRequest req) {
        long start = System.currentTimeMillis();
        String base = trimTrailingSlash(req.getWso2BaseUrl());
        int limit = req.getLimit() == null ? 20 : req.getLimit();
        int offset = req.getOffset() == null ? 0 : req.getOffset();

        // 1. Try APIM DevPortal endpoint with Bearer (preferred on APIM)
        if (StringUtils.hasText(req.getClientId()) && StringUtils.hasText(req.getClientSecret())) {
            Wso2TenantsResponse devportal = tryDevPortal(base, req, limit, offset, start);
            if (devportal != null && devportal.isSuccess() && hasTenants(devportal)) {
                return devportal;
            }
            if (devportal != null) {
                log.info("DevPortal /tenants returned {} — falling back to IS endpoint",
                        devportal.isSuccess() ? "no tenants" : "error: " + devportal.getErrorMessage());
            }
        }

        // 2. Fall back to WSO2 IS endpoint with Basic auth
        return tryIsEndpointBasic(base, req, limit, offset, start);
    }

    private Wso2TenantsResponse tryDevPortal(String base, Wso2TenantsRequest req,
                                             int limit, int offset, long start) {
        String token;
        try {
            token = acquireToken(base, req);
        } catch (Exception e) {
            log.warn("OAuth password grant for DevPortal call failed: {}", e.getMessage());
            return null;
        }
        if (!StringUtils.hasText(token)) {
            log.warn("OAuth password grant returned no access_token — skipping DevPortal call");
            return null;
        }

        try {
            log.info("Listing tenants from {} (Bearer)", base + DEVPORTAL_PATH);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = webClient.get()
                    .uri(base + DEVPORTAL_PATH + "?state=active&limit={limit}&offset={offset}",
                            limit, offset)
                    .header("Authorization", "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(props.getVerify().getTimeoutSeconds()))
                    .block();
            log.info("DevPortal /tenants raw response: {}", body);
            return buildResponse(body, "tenants", "list", start);
        } catch (WebClientResponseException e) {
            log.warn("DevPortal /tenants failed: {} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return Wso2TenantsResponse.builder()
                    .success(false)
                    .tenants(Collections.emptyList())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .errorMessage("DevPortal " + DEVPORTAL_PATH + " returned "
                            + e.getStatusCode() + " " + e.getResponseBodyAsString())
                    .build();
        } catch (Exception e) {
            log.warn("DevPortal /tenants failed: {}", e.getMessage());
            return Wso2TenantsResponse.builder()
                    .success(false)
                    .tenants(Collections.emptyList())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private Wso2TenantsResponse tryIsEndpointBasic(String base, Wso2TenantsRequest req,
                                                   int limit, int offset, long start) {
        String basic = Base64.getEncoder().encodeToString(
                (req.getUsername() + ":" + req.getPassword()).getBytes());
        try {
            log.info("Listing tenants from {} (Basic)", base + IS_PATH);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = webClient.get()
                    .uri(base + IS_PATH + "?limit={limit}&offset={offset}", limit, offset)
                    .header("Authorization", "Basic " + basic)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(props.getVerify().getTimeoutSeconds()))
                    .block();
            log.info("IS /tenants raw response: {}", body);
            return buildResponse(body, "tenants", "list", start);
        } catch (WebClientResponseException e) {
            log.warn("IS /tenants failed: {} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return Wso2TenantsResponse.builder()
                    .success(false)
                    .tenants(Collections.emptyList())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .errorMessage("IS " + IS_PATH + " returned "
                            + e.getStatusCode() + " " + e.getResponseBodyAsString())
                    .build();
        } catch (Exception e) {
            log.warn("IS /tenants failed: {}", e.getMessage());
            return Wso2TenantsResponse.builder()
                    .success(false)
                    .tenants(Collections.emptyList())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    private String acquireToken(String baseUrl, Wso2TenantsRequest req) {
        String basic = Base64.getEncoder().encodeToString(
                (req.getClientId() + ":" + req.getClientSecret()).getBytes());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("username", req.getUsername());
        form.add("password", req.getPassword());
        form.add("scope", TENANT_SCOPES);

        Map<String, Object> body = webClient.post()
                .uri(baseUrl + TOKEN_PATH)
                .header("Authorization", "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(props.getVerify().getTimeoutSeconds()))
                .block();
        if (body != null) {
            Object granted = body.get("scope");
            if (granted != null) {
                log.info("Token granted scopes: {}", granted);
            }
        }
        Object t = body != null ? body.get("access_token") : null;
        return t == null ? null : t.toString();
    }

    private static Wso2TenantsResponse buildResponse(Map<String, Object> body,
                                                     String primaryKey, String altKey, long start) {
        List<Wso2TenantsResponse.TenantInfo> tenants = parseTenants(body, primaryKey, altKey);
        return Wso2TenantsResponse.builder()
                .success(true)
                .totalResults(asInt(body == null ? null
                        : body.getOrDefault("totalResults", body.get("count"))))
                .tenants(tenants)
                .elapsedMs(System.currentTimeMillis() - start)
                .fetchedAt(Instant.now())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Wso2TenantsResponse.TenantInfo> parseTenants(Map<String, Object> body,
                                                                     String primaryKey, String altKey) {
        if (body == null) return Collections.emptyList();
        Object raw = body.get(primaryKey);
        if (raw == null) raw = body.get(altKey);
        if (!(raw instanceof List<?> items)) return Collections.emptyList();
        List<Wso2TenantsResponse.TenantInfo> out = new ArrayList<>(items.size());
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Map<String, Object> mm = (Map<String, Object>) m;
            out.add(Wso2TenantsResponse.TenantInfo.builder()
                    .domain(asString(mm.get("domain")))
                    .id(asString(mm.get("id")))
                    .lifecycleStatus(asString(mm.get("lifecycleStatus")))
                    .active(asBoolean(mm.get("active")))
                    .build());
        }
        return out;
    }

    private static boolean hasTenants(Wso2TenantsResponse r) {
        return r.getTenants() != null && !r.getTenants().isEmpty();
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s && !s.isBlank()) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private static Boolean asBoolean(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return Boolean.parseBoolean(s);
        return null;
    }

    private static String trimTrailingSlash(String s) {
        if (!StringUtils.hasText(s)) return s;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
