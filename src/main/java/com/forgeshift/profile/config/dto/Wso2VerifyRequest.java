package com.forgeshift.profile.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Verify credentials without saving a profile. Pass the connection details
 * directly. Use {@code companyName + wso2Tenant + profileName} to verify an
 * already-saved profile via {@code POST /wso2/profiles/{id}/verify} instead.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wso2VerifyRequest {

    @NotBlank private String wso2BaseUrl;
    @NotBlank private String username;
    @NotBlank private String password;
    @NotBlank private String clientId;
    @NotBlank private String clientSecret;

    private boolean trustSelfSigned;

    /** Optional - if true, also fetch /tenant-info/admin for a richer result. */
    private Boolean includeTenantInfo;
}
