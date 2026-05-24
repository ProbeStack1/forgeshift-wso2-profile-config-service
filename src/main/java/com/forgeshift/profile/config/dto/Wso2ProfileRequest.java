package com.forgeshift.profile.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    @Schema(description = "WSO2 tenant id", example = "carbon.super",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String wso2Tenant;

    @NotBlank
    @Schema(description = "Profile name (unique per company+tenant)", example = "primary",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String profileName;

    @NotBlank
    @Schema(description = "WSO2 management plane URL", example = "https://34.133.77.23:9443",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String wso2BaseUrl;

    @NotBlank private String username;
    @NotBlank private String password;
    @NotBlank private String clientId;
    @NotBlank private String clientSecret;

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
