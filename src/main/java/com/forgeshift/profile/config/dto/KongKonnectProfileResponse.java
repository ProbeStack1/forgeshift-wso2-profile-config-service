package com.forgeshift.profile.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forgeshift.profile.config.domain.KongKonnectProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KongKonnectProfileResponse {

    private String id;
    private String companyName;
    private String profileName;
    private String konnectBaseUrl;
    private String konnectAccessTokenMasked;
    private String controlPlaneId;
    private String region;
    private String notes;
    private String createdBy;
    private String lastModifiedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastVerifiedAt;
    private String lastVerifiedControlPlaneName;

    public static KongKonnectProfileResponse from(KongKonnectProfile p) {
        return KongKonnectProfileResponse.builder()
                .id(p.getId())
                .companyName(p.getCompanyName())
                .profileName(p.getProfileName())
                .konnectBaseUrl(p.getKonnectBaseUrl())
                .konnectAccessTokenMasked(mask(p.getKonnectAccessToken()))
                .controlPlaneId(p.getControlPlaneId())
                .region(p.getRegion())
                .notes(p.getNotes())
                .createdBy(p.getCreatedBy())
                .lastModifiedBy(p.getLastModifiedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .lastVerifiedAt(p.getLastVerifiedAt())
                .lastVerifiedControlPlaneName(p.getLastVerifiedControlPlaneName())
                .build();
    }

    private static String mask(String s) {
        if (s == null || s.isBlank()) return null;
        if (s.length() <= 6) return "***";
        return s.substring(0, 6) + "...";
    }
}
