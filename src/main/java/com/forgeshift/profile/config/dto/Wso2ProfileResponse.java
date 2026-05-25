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
    private String wso2Tenant;
    private String profileName;
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

    /** Tenant domains discovered from WSO2 at create time. */
    private List<String> discoveredTenants;
    private Instant discoveredTenantsAt;

    public static Wso2ProfileResponse from(Wso2Profile p) {
        return Wso2ProfileResponse.builder()
                .id(p.getId())
                .companyName(p.getCompanyName())
                .wso2Tenant(p.getWso2Tenant())
                .profileName(p.getProfileName())
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
