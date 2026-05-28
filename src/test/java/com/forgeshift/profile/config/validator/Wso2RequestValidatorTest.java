package com.forgeshift.profile.config.validator;

import com.forgeshift.profile.config.domain.Wso2Profile;
import com.forgeshift.profile.config.dto.Wso2ProfileInfoRequest;
import com.forgeshift.profile.config.dto.Wso2ProfileRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Wso2RequestValidatorTest {

    private final Wso2RequestValidator validator = new Wso2RequestValidator();

    @Test
    void validateInfoRequestNormalizesSharedFields() {
        Wso2ProfileInfoRequest request = Wso2ProfileInfoRequest.builder()
                .companyName(" ProbeStack ")
                .profileName(" primary ")
                .wso2BaseUrl(" https://34.133.77.23:9443/ ")
                .username(" admin ")
                .password(" admin ")
                .userEmail(" Admin@ForgeCrux.com ")
                .build();

        validator.validateInfoRequest(request);

        assertThat(request.getCompanyName()).isEqualTo("probestack");
        assertThat(request.getProfileName()).isEqualTo("primary");
        assertThat(request.getWso2BaseUrl()).isEqualTo("https://34.133.77.23:9443");
        assertThat(request.getUsername()).isEqualTo("admin");
        assertThat(request.getUserEmail()).isEqualTo("admin@forgecrux.com");
    }

    @Test
    void validateInfoRequestRequiresClientIdAndSecretTogether() {
        Wso2ProfileInfoRequest request = Wso2ProfileInfoRequest.builder()
                .companyName("probestack")
                .wso2BaseUrl("https://34.133.77.23:9443")
                .username("admin")
                .password("admin")
                .clientId("client-id")
                .build();

        assertThatThrownBy(() -> validator.validateInfoRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("clientId and clientSecret must be provided together");
    }

    @Test
    void validateCreateRequestRejectsInvalidProfileName() {
        Wso2ProfileRequest request = baseCreateRequest();
        request.setProfileName("bad profile");

        assertThatThrownBy(() -> validator.validateCreateRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("profileName must contain only alphanumeric characters, hyphens, and underscores");
    }

    @Test
    void validateUniqueWso2ConfigRejectsDuplicateActiveUrlAndTenant() {
        Wso2Profile existing = Wso2Profile.builder()
                .id("probestack|primary")
                .companyName("probestack")
                .profileName("primary")
                .wso2BaseUrl("https://34.133.77.23:9443/")
                .defaultWso2Tenant("carbon.super")
                .status("ACTIVE")
                .build();

        assertThatThrownBy(() -> validator.validateUniqueWso2Config(
                List.of(existing),
                "https://34.133.77.23:9443",
                "carbon.super",
                null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Active WSO2 profile already exists");
    }

    @Test
    void validateUniqueWso2ConfigIgnoresCurrentProfileDuringUpdate() {
        Wso2Profile existing = Wso2Profile.builder()
                .id("probestack|primary")
                .companyName("probestack")
                .profileName("primary")
                .wso2BaseUrl("https://34.133.77.23:9443")
                .defaultWso2Tenant("carbon.super")
                .status("ACTIVE")
                .build();

        validator.validateUniqueWso2Config(
                List.of(existing),
                "https://34.133.77.23:9443",
                "carbon.super",
                "probestack|primary");
    }

    private Wso2ProfileRequest baseCreateRequest() {
        return Wso2ProfileRequest.builder()
                .companyName("probestack")
                .profileName("primary")
                .defaultWso2Tenant("carbon.super")
                .wso2BaseUrl("https://34.133.77.23:9443")
                .username("admin")
                .password("admin")
                .userEmail("admin@forgecrux.com")
                .build();
    }
}
