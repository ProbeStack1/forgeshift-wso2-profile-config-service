package com.forgeshift.profile.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Discover tenant domains on a WSO2 instance using admin credentials. Use
 * this before {@code POST /wso2/profiles} so the caller can pick a real
 * tenant value instead of guessing one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wso2TenantsRequest {

    @NotBlank
    @Schema(description = "WSO2 management plane URL", example = "https://34.133.77.23:9443",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String wso2BaseUrl;

    @NotBlank
    @Schema(description = "Super-tenant admin username", example = "admin",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank
    @Schema(description = "Super-tenant admin password", example = "admin",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    /**
     * OAuth2 client credentials used to obtain a Bearer token via password
     * grant. Provide DCR-issued values here — without them the client falls
     * back to Basic auth, which some WSO2 APIM installs silently return as
     * an empty tenant list.
     */
    @Schema(description = "Optional DCR clientId. When present, the client uses Bearer auth.")
    private String clientId;

    @Schema(description = "Optional DCR clientSecret. Required together with clientId for Bearer auth.")
    private String clientSecret;

    @Schema(description = "Skip TLS hostname/cert validation (lab/self-signed only)")
    private boolean trustSelfSigned;

    @Schema(description = "Page size for the tenants listing", example = "20")
    private Integer limit;

    @Schema(description = "Page offset for the tenants listing", example = "0")
    private Integer offset;
}
