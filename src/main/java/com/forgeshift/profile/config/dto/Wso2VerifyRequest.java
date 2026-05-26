package com.forgeshift.profile.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Verify credentials without saving a profile. Pass the connection details
 * directly. Use {@code POST /wso2/profiles/verify-saved} to verify an
 * already-persisted profile.
 *
 * <p>{@code clientId} / {@code clientSecret} are optional — when omitted,
 * the service calls WSO2 Dynamic Client Registration first (idempotent on
 * the derived {@code clientName}) and uses the generated pair. Pass
 * {@code companyName} / {@code profileName} so DCR can produce a stable,
 * meaningful client name; otherwise a generic {@code forgeshift_verify}
 * is used.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wso2VerifyRequest {

    @NotBlank private String wso2BaseUrl;
    @NotBlank private String username;
    @NotBlank private String password;

    @Schema(description = "Optional. Auto-generated via WSO2 DCR if omitted.")
    private String clientId;

    @Schema(description = "Optional. Auto-generated via WSO2 DCR if omitted.")
    private String clientSecret;

    /** Optional — used as the DCR clientName seed when clientId/secret are absent. */
    @Schema(description = "Optional. Improves the DCR clientName when creds need to be generated.")
    private String companyName;

    @Schema(description = "Optional. Improves the DCR clientName when creds need to be generated.")
    private String profileName;

    private boolean trustSelfSigned;

    /** Optional - if true, also fetch /tenant-info/admin for a richer result. */
    private Boolean includeTenantInfo;
}
