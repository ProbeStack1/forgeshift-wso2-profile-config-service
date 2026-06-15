package com.forgeshift.profile.config.service;

import com.forgeshift.profile.config.client.Wso2DcrClient;
import com.forgeshift.profile.config.client.Wso2TenantsClient;
import com.forgeshift.profile.config.client.Wso2VerifyClient;
import com.forgeshift.profile.config.domain.Wso2Profile;
import com.forgeshift.profile.config.dto.Wso2ProfileRequest;
import com.forgeshift.profile.config.repository.Wso2ProfileRepository;
import com.forgeshift.profile.config.validator.Wso2RequestValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the {@code update()} OAuth-client refresh: when the caller does not bring an explicit
 * clientId+clientSecret pair, update re-runs DCR (mirrors create) so a profile whose stored client
 * went stale (WSO2 pod recreated → {@code invalid_client}) is healed by an update, instead of keeping
 * the dead client. An explicit pair is honoured without a DCR call.
 */
@ExtendWith(MockitoExtension.class)
class Wso2ProfileServiceTest {

    @Mock Wso2ProfileRepository repository;
    @Mock Wso2VerifyClient verifyClient;
    @Mock Wso2DcrClient dcrClient;
    @Mock Wso2TenantsClient tenantsClient;
    @Mock Wso2RequestValidator validator;
    @InjectMocks Wso2ProfileService service;

    private Wso2Profile existing(String clientId, String secret) {
        return Wso2Profile.builder()
                .id("probestack|p1").companyName("probestack").profileName("p1")
                .defaultWso2Tenant("carbon.super").wso2BaseUrl("https://old-host:9443")
                .username("admin").password("admin")
                .clientId(clientId).clientSecret(secret)
                .status("ACTIVE").build();
    }

    private Wso2ProfileRequest req(String clientId, String secret) {
        return Wso2ProfileRequest.builder()
                .companyName("probestack").profileName("p1").defaultWso2Tenant("carbon.super")
                .wso2BaseUrl("https://wso2.probestack.io:9443").username("admin").password("admin")
                .clientId(clientId).clientSecret(secret).trustSelfSigned(true).status("ACTIVE").build();
    }

    private void commonStubs(Wso2Profile existing) {
        when(validator.normalizeRequiredCompanyName("probestack")).thenReturn("probestack");
        when(validator.normalizeRequiredProfileName("p1")).thenReturn("p1");
        when(repository.findByCompanyNameAndProfileName("probestack", "p1")).thenReturn(Optional.of(existing));
        when(repository.findByCompanyName("probestack")).thenReturn(List.of(existing));
        when(repository.save(any(Wso2Profile.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void update_withoutCreds_reRunsDcr_andRefreshesStaleClient() {
        Wso2Profile existing = existing("STALE_ID", "STALE_SECRET");
        commonStubs(existing);
        when(dcrClient.register(any())).thenReturn(
                Wso2DcrClient.DcrCredentials.builder().clientId("FRESH_ID").clientSecret("FRESH_SECRET").build());

        service.update("probestack", "p1", req(null, null));

        verify(dcrClient).register(any());
        assertThat(existing.getClientId()).isEqualTo("FRESH_ID");
        assertThat(existing.getClientSecret()).isEqualTo("FRESH_SECRET");
        assertThat(existing.getWso2BaseUrl()).isEqualTo("https://wso2.probestack.io:9443");
    }

    @Test
    void update_withExplicitCreds_usesThem_withoutDcr() {
        Wso2Profile existing = existing("STALE_ID", "STALE_SECRET");
        commonStubs(existing);

        service.update("probestack", "p1", req("BYO_ID", "BYO_SECRET"));

        verify(dcrClient, never()).register(any());
        assertThat(existing.getClientId()).isEqualTo("BYO_ID");
        assertThat(existing.getClientSecret()).isEqualTo("BYO_SECRET");
    }
}
