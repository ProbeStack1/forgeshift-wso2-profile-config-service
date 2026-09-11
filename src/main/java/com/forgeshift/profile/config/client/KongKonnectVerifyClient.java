package com.forgeshift.profile.config.client;

import com.forgeshift.profile.config.config.ProfileConfigProperties;
import com.forgeshift.profile.config.domain.KongKonnectControlPlane;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Verifies a Kong Konnect PAT by listing available control planes.
 */
@Slf4j
@Component
public class KongKonnectVerifyClient {

    private static final Pattern HOST_LABEL = Pattern.compile("[a-z0-9-]{1,63}");

    private final WebClient webClient;
    private final ProfileConfigProperties props;

    public KongKonnectVerifyClient(@Qualifier("kongKonnectVerifyWebClient") WebClient webClient,
                                   ProfileConfigProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @SuppressWarnings("unchecked")
    public KongKonnectVerifyResponse verify(KongKonnectVerifyRequest req) {
        List<KongKonnectControlPlane> controlPlanes = fetchControlPlanes(req.getRegion(), req.getKonnectPat());
        List<KongKonnectVerifyResponse.ControlPlaneInfo> responseControlPlanes = controlPlanes.stream()
                .map(cp -> new KongKonnectVerifyResponse.ControlPlaneInfo(
                        cp.getControlPlaneId(),
                        cp.getControlPlaneName()))
                .collect(Collectors.toList());

        return KongKonnectVerifyResponse.builder()
                .companyName(req.getCompanyName())
                .adminUrl(req.getAdminUrl())
                .region(req.getRegion())
                .controlPlanes(responseControlPlanes)
                .build();
    }

    @SuppressWarnings("unchecked")
    public List<KongKonnectControlPlane> fetchControlPlanes(String region, String konnectPat) {
        try {
            String url = "https://" + hostLabel(region) + ".api.konghq.com/v2/control-planes";

            Map<String, Object> body = webClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + konnectPat)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(props.getVerify().getTimeoutSeconds()))
                    .block();

            Object data = body != null ? body.get("data") : null;
            if (!(data instanceof List<?> items)) {
                return Collections.emptyList();
            }

            return items.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(cp -> new KongKonnectControlPlane(str(cp.get("id")), str(cp.get("name"))))
                    .collect(Collectors.toList());
        } catch (WebClientResponseException e) {
            throw new IllegalArgumentException(
                    "Konnect returned " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * The region becomes the first label of the Konnect host, so it may only be one: letters,
     * digits and hyphens. Anything else - {@code evil.example/#} - would send the request, and
     * the token in its Authorization header, to another host. A profile update that keeps the
     * stored token calls this with whatever region the caller sent.
     */
    private static String hostLabel(String region) {
        String label = region == null ? "" : region.strip().toLowerCase(Locale.ROOT);
        if (!HOST_LABEL.matcher(label).matches()) {
            throw new IllegalArgumentException("region must be a Konnect region code such as us, eu or au");
        }
        return label;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}
