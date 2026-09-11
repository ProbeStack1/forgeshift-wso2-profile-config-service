package com.forgeshift.profile.config.client;

import com.forgeshift.profile.config.config.ProfileConfigProperties;
import com.forgeshift.profile.config.domain.KongKonnectControlPlane;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The region is spliced into the Konnect host, and the request carries a token - since profile
 * updates may keep the stored one, a token the caller never had. So the region may only ever
 * pick a {@code {region}.api.konghq.com} host.
 */
class KongKonnectVerifyClientTest {

    private final List<ClientRequest> requests = new ArrayList<>();
    private KongKonnectVerifyClient client;

    @BeforeEach
    void setUp() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requests.add(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"data\":[{\"id\":\"cp-1\",\"name\":\"default\"}]}")
                            .build());
                })
                .build();
        client = new KongKonnectVerifyClient(webClient, new ProfileConfigProperties());
    }

    @ParameterizedTest
    @ValueSource(strings = {"us", "eu", "EU", " au "})
    void regionCode_picksItsKonnectHost(String region) {
        List<KongKonnectControlPlane> controlPlanes = client.fetchControlPlanes(region, "kpat_token");

        assertThat(controlPlanes).extracting(KongKonnectControlPlane::getControlPlaneId).containsExactly("cp-1");
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.url().toString()).isEqualTo(
                    "https://" + region.strip().toLowerCase(Locale.ROOT) + ".api.konghq.com/v2/control-planes");
            assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer kpat_token");
        });
    }

    /** Each would otherwise send the request, Authorization header and all, to evil.example. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"evil.example/#", "evil.example?", "evil.example:443/", "user@evil.example/", "   "})
    void anythingButOneHostLabel_isRejectedBeforeAnyRequest(String region) {
        assertThatThrownBy(() -> client.fetchControlPlanes(region, "kpat_token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("region");

        assertThat(requests).isEmpty();
    }
}
