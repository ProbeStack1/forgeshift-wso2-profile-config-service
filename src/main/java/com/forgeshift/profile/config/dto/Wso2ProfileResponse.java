package com.forgeshift.profile.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forgeshift.profile.config.domain.Wso2Profile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Public representation of a Wso2Profile. Password and clientSecret are never
 * returned — they exist only server-side for outbound calls to WSO2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Wso2ProfileResponse {

    private String id;
    private String companyName;
    private String profileName;

    /** The single tenant this profile binds to (user-chosen at save time). */
    private String defaultWso2Tenant;

    private String wso2BaseUrl;
    private String username;
    private String clientId;
    private boolean trustSelfSigned;
    private String status;
    private String notes;
    private String createdBy;
    private String lastModifiedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastVerifiedAt;
    private String lastVerifiedTenantInfo;

    /** Raw tenant list returned by the WSO2 tenants API at create time. */
    private List<String> discoveredTenants;
    private Instant discoveredTenantsAt;

    public static Wso2ProfileResponse from(Wso2Profile p) {
        return Wso2ProfileResponse.builder()
                .id(p.getId())
                .companyName(p.getCompanyName())
                .profileName(p.getProfileName())
                .defaultWso2Tenant(p.getDefaultWso2Tenant())
                .wso2BaseUrl(p.getWso2BaseUrl())
                .username(p.getUsername())
                .clientId(p.getClientId())
                .trustSelfSigned(p.isTrustSelfSigned())
                .status(p.getStatus())
                .notes(p.getNotes())
                .createdBy(p.getCreatedBy())
                .lastModifiedBy(p.getLastModifiedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .lastVerifiedAt(p.getLastVerifiedAt())
                .lastVerifiedTenantInfo(p.getLastVerifiedTenantInfo())
                .discoveredTenants(p.getDiscoveredTenants())
                .discoveredTenantsAt(p.getDiscoveredTenantsAt())
                .build();
    }
}
