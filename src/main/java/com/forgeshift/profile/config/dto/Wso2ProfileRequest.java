package com.forgeshift.profile.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wso2ProfileRequest {

    @NotBlank
    @Schema(description = "Multi-tenancy partner id", example = "probestack",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String companyName;

    @NotBlank
    @Schema(description = "Profile name (unique per company)", example = "primary",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String profileName;

    /**
     * Optional. When omitted, the service calls the WSO2 tenants API using
     * the supplied {@code username}/{@code password} and stores every
     * discovered domain (plus {@code carbon.super}) in the profile's
     * tenants array. Provide an explicit list to bind the profile to a
     * specific subset.
     */
    @Schema(description = "Tenants this profile manages. Auto-discovered if omitted.",
            example = "[\"carbon.super\", \"bank.local\"]")
    private List<String> tenants;

    @NotBlank
    @Schema(description = "WSO2 management plane URL", example = "https://34.133.77.23:9443",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String wso2BaseUrl;

    @NotBlank private String username;
    @NotBlank private String password;

    /**
     * Optional. When omitted, the service calls WSO2 Dynamic Client Registration
     * (using {@code username}/{@code password}) and persists the generated pair.
     * Provide a value only if you want to bring an existing DCR client.
     */
    @Schema(description = "Optional. Auto-generated via WSO2 DCR if omitted.")
    private String clientId;

    @Schema(description = "Optional. Auto-generated via WSO2 DCR if omitted.")
    private String clientSecret;

    private boolean trustSelfSigned;
    private String notes;

    /**
     * Optional lifecycle status. Defaults to ACTIVE on create. The discovery
     * service skips profiles where status != ACTIVE.
     */
    @Schema(description = "Lifecycle status", example = "ACTIVE",
            allowableValues = {"ACTIVE", "INACTIVE", "SUSPENDED"})
    private String status;

    /** Audit only — who is creating/updating this profile. */
    private String userEmail;
}
