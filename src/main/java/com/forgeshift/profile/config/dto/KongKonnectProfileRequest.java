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
public class KongKonnectProfileRequest {

    @NotBlank
    @Schema(description = "Multi-tenancy partner id", example = "probestack",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String companyName;

    @NotBlank
    @Schema(description = "Profile name (unique per company)", example = "primary",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String profileName;

    @NotBlank
    @Schema(description = "Kong Konnect admin API URL", example = "https://us.api.konghq.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String adminUrl;

    @NotBlank
    @Schema(description = "Personal Access Token (kpat_...)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String konnectPat;

    @NotBlank
    @Schema(description = "Region label (us, eu, au)")
    private String region;

    @Schema(description = "Control plane used when a request does not name one")
    private String defaultControlPlane;
    @Schema(description = "Make this the profile used when a request does not name one")
    private Boolean defaultProfile;
    @NotBlank
    private String userEmail;
}
