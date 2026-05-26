package com.forgeshift.profile.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Result of {@code POST /wso2/profiles/info}. Carries the discovered
 * tenant list so the UI can show a picker, plus enough echo of the
 * caller's input that the frontend can pass straight through to the
 * {@code POST /wso2/profiles/save} call without re-collecting fields.
 *
 * <p>Secrets ({@code password}, {@code clientSecret}) are never echoed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Wso2ProfileInfoResponse {

    /** Whether the WSO2 round-trip succeeded. False → see errorMessage. */
    private boolean success;
    private String errorMessage;

    // Echo of caller input (no secrets) so a UI can stay stateless ─────
    private String companyName;
    private String profileName;
    private String wso2BaseUrl;
    private String username;
    private boolean trustSelfSigned;
    private String notes;
    private String userEmail;

    /**
     * Domains the WSO2 tenants API reported for this instance — the user
     * picks one as {@code defaultWso2Tenant} on the follow-up save call.
     * Mirrors what gets stored in {@code Wso2Profile.discoveredTenants}.
     */
    private List<String> discoveredTenants;
    private Instant discoveredTenantsAt;
}
