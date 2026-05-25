package com.forgeshift.profile.config.client;

import com.forgeshift.profile.config.config.ProfileConfigProperties;
import com.forgeshift.profile.config.dto.Wso2TenantsRequest;
import com.forgeshift.profile.config.dto.Wso2TenantsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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
 * Calls the WSO2 IS tenant-management REST API
 * ({@code GET /api/server/v1/tenants}) to enumerate tenant domains. Lets a
 * client discover valid {@code wso2Tenant} values before creating a profile.
 *
 * The endpoint is super-tenant-only and accepts Basic auth with admin
 * credentials in default WSO2 deployments.
 */
@Slf4j
@Component
public class Wso2TenantsClient {

    private static final String TENANTS_PATH = "/api/server/v1/tenants";

    private final WebClient webClient;
    private final ProfileConfigProperties props;

    public Wso2TenantsClient(@Qualifier("wso2VerifyWebClient") WebClient webClient,
                             ProfileConfigProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    public Wso2TenantsResponse listTenants(Wso2TenantsRequest req) {
        long start = System.currentTimeMillis();
        try {
            String base = trimTrailingSlash(req.getWso2BaseUrl());
            int limit = req.getLimit() == null ? 20 : req.getLimit();
            int offset = req.getOffset() == null ? 0 : req.getOffset();
            String basic = Base64.getEncoder().encodeToString(
                    (req.getUsername() + ":" + req.getPassword()).getBytes());

            @SuppressWarnings("unchecked")
            Map<String, Object> body = webClient.get()
                    .uri(base + TENANTS_PATH + "?limit={limit}&offset={offset}", limit, offset)
                    .header("Authorization", "Basic " + basic)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(props.getVerify().getTimeoutSeconds()))
                    .block();

            return Wso2TenantsResponse.builder()
                    .success(true)
                    .totalResults(asInt(body == null ? null : body.get("totalResults")))
                    .tenants(parseTenants(body))
                    .elapsedMs(System.currentTimeMillis() - start)
                    .fetchedAt(Instant.now())
                    .build();
        } catch (WebClientResponseException e) {
            log.warn("WSO2 tenants listing failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Wso2TenantsResponse.builder()
                    .success(false)
                    .tenants(Collections.emptyList())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .errorMessage("WSO2 " + TENANTS_PATH + " returned "
                            + e.getStatusCode() + " " + e.getResponseBodyAsString())
                    .build();
        } catch (Exception e) {
            log.warn("WSO2 tenants listing failed: {}", e.getMessage());
            return Wso2TenantsResponse.builder()
                    .success(false)
                    .tenants(Collections.emptyList())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Wso2TenantsResponse.TenantInfo> parseTenants(Map<String, Object> body) {
        if (body == null) return Collections.emptyList();
        Object raw = body.get("tenants");
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
