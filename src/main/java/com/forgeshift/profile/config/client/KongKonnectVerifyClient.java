package com.forgeshift.profile.config.client;

import com.forgeshift.profile.config.config.ProfileConfigProperties;
import com.forgeshift.profile.config.dto.KongKonnectVerifyRequest;
import com.forgeshift.profile.config.dto.KongKonnectVerifyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Verifies a Kong Konnect PAT + control plane id by calling
 * GET /v2/control-planes/{controlPlaneId}.
 */
@Slf4j
@Component
public class KongKonnectVerifyClient {

    private final WebClient webClient;
    private final ProfileConfigProperties props;

    public KongKonnectVerifyClient(@Qualifier("kongKonnectVerifyWebClient") WebClient webClient,
                                   ProfileConfigProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @SuppressWarnings("unchecked")
    public KongKonnectVerifyResponse verify(KongKonnectVerifyRequest req) {
        long start = System.currentTimeMillis();
        try {
            String url = trimSlash(req.getKonnectBaseUrl())
                    + "/v2/control-planes/" + req.getControlPlaneId();

            Map<String, Object> body = webClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + req.getKonnectAccessToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(props.getVerify().getTimeoutSeconds()))
                    .block();

            String name = body != null ? str(body.get("name")) : null;
            String region = null;
            if (body != null && body.get("config") instanceof Map<?, ?> cfg) {
                region = str(((Map<String, Object>) cfg).get("control_plane_endpoint"));
            }

            return KongKonnectVerifyResponse.builder()
                    .success(true)
                    .controlPlaneName(name)
                    .controlPlaneRegion(region)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .verifiedAt(Instant.now())
                    .build();
        } catch (WebClientResponseException e) {
            return KongKonnectVerifyResponse.builder()
                    .success(false)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .errorMessage("Konnect returned " + e.getStatusCode() + " " + e.getResponseBodyAsString())
                    .build();
        } catch (Exception e) {
            return KongKonnectVerifyResponse.builder()
                    .success(false)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static String trimSlash(String s) {
        if (!StringUtils.hasText(s)) return s;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
