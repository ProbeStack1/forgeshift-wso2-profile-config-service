package com.forgeshift.profile.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forgeshift.profile.config.domain.Wso2Profile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Public representation of a Wso2Profile. Secrets are masked: only the first
 * 4 characters of password / clientSecret are returned, followed by "...".
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
    private String passwordMasked;
    private String clientId;
    private String clientSecretMasked;
    private boolean trustSelfSigned;
    private String status;
    private String notes;
    private String createdBy;
    private String lastModifiedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastVerifiedAt;
    private String lastVerifiedTenantInfo;

    public static Wso2ProfileResponse from(Wso2Profile p) {
        return Wso2ProfileResponse.builder()
                .id(p.getId())
                .companyName(p.getCompanyName())
                .wso2Tenant(p.getWso2Tenant())
                .profileName(p.getProfileName())
                .wso2BaseUrl(p.getWso2BaseUrl())
                .username(p.getUsername())
                .passwordMasked(mask(p.getPassword()))
                .clientId(p.getClientId())
                .clientSecretMasked(mask(p.getClientSecret()))
                .trustSelfSigned(p.isTrustSelfSigned())
                .status(p.getStatus())
                .notes(p.getNotes())
                .createdBy(p.getCreatedBy())
                .lastModifiedBy(p.getLastModifiedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .lastVerifiedAt(p.getLastVerifiedAt())
                .lastVerifiedTenantInfo(p.getLastVerifiedTenantInfo())
                .build();
    }

    private static String mask(String s) {
        if (s == null || s.isBlank()) return null;
        if (s.length() <= 4) return "***";
        return s.substring(0, 4) + "...";
    }
}
