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

    @NotBlank(groups = OnCreate.class)
    @Schema(description = "Personal Access Token (kpat_...). Required on create. On update, leave it "
            + "out, or send back the konnectPatStored mask, to keep the stored token - unless adminUrl "
            + "changes, which needs the token again.")
    private String konnectPat;

    @NotBlank
    @Schema(description = "Region label (us, eu, au)")
    private String region;

    @Schema(description = "Id of the control plane used when a request does not name one. On update, "
            + "leave it out to keep the current default - it is dropped if Konnect no longer lists that "
            + "control plane - or send an empty string to clear it.")
    private String defaultControlPlane;
    @Schema(description = "Make this the profile used when a request does not name one")
    private Boolean defaultProfile;
    @NotBlank
    private String userEmail;
}
