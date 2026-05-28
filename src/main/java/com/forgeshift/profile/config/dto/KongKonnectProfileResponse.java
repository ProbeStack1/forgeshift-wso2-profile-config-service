package com.forgeshift.profile.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forgeshift.profile.config.domain.KongKonnectProfile;
import com.forgeshift.profile.config.domain.KongKonnectControlPlane;
import com.forgeshift.profile.config.domain.ProfileStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KongKonnectProfileResponse {

    private String id;
    private String profileName;
    private String companyName;
    private String adminUrl;
    private String konnectPat;
    private String region;
    private List<KongKonnectControlPlane> controlPlanes;
    private ProfileStatus status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime lastUpdatedAt;
    private String lastUpdatedBy;

    public static KongKonnectProfileResponse from(KongKonnectProfile p) {
        return KongKonnectProfileResponse.builder()
                .id(p.getId())
                .profileName(p.getProfileName())
                .companyName(p.getCompanyName())
                .adminUrl(p.getAdminUrl())
                .konnectPat(p.getKonnectPat())
                .region(p.getRegion())
                .controlPlanes(p.getControlPlanes())
                .status(p.getStatus() == null ? ProfileStatus.ACTIVE : p.getStatus())
                .createdAt(p.getCreatedAt())
                .createdBy(p.getCreatedBy())
                .lastUpdatedAt(p.getLastUpdatedAt())
                .lastUpdatedBy(p.getLastUpdatedBy())
                .build();
    }
}
