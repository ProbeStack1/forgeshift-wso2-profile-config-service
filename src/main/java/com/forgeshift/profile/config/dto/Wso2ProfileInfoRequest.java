package com.forgeshift.profile.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Probe-only payload for {@code POST /wso2/profiles/info}.
 *
 * <p>The service uses these credentials to DCR a client + acquire a Bearer
 * token + enumerate the tenant domains visible on the WSO2 instance, then
 * returns them so the UI can show a picker. <strong>Nothing is persisted</strong>
 * — the user follows up with {@code POST /wso2/profiles/save} once they
 * pick a {@code defaultWso2Tenant}.
 *
 * <p>Same fields as {@link Wso2ProfileRequest} except there's no
 * {@code defaultWso2Tenant} (that's the whole point — the user hasn't
 * picked one yet) and no lifecycle {@code status}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wso2ProfileInfoRequest {

    @NotBlank
    @Schema(description = "Multi-tenancy partner id", example = "probestack",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String companyName;

    /**
     * Optional at the info stage — the user typically hasn't decided on a
     * profile name yet when probing. When omitted, the DCR clientName
     * defaults to {@code forgeshift_<companyName>_info_probe}; supply a
     * profileName up front to reuse the same DCR app on the follow-up
     * save call.
     */
    @Schema(description = "Profile name. Optional at this stage; passing it here lets DCR reuse the same OAuth client on the follow-up save call.",
            example = "primary")
    private String profileName;

    @NotBlank
    @Schema(description = "WSO2 management plane URL", example = "https://34.133.77.23:9443",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String wso2BaseUrl;

    @NotBlank private String username;
    @NotBlank private String password;

    /**
     * Optional: bring your own OAuth client. When absent, the info call
     * performs DCR against the WSO2 instance to obtain one — exactly the
     * same as the save flow, so the discovered tenant list matches what
     * the save endpoint will see.
     */
    @Schema(description = "Optional. Auto-generated via WSO2 DCR if omitted.")
    private String clientId;

    @Schema(description = "Optional. Auto-generated via WSO2 DCR if omitted.")
    private String clientSecret;

    private boolean trustSelfSigned;
    private String notes;
    private String userEmail;
}
