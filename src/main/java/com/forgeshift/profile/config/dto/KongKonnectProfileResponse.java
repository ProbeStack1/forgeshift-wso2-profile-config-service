package com.forgeshift.profile.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forgeshift.profile.config.domain.KongKonnectControlPlane;
import com.forgeshift.profile.config.domain.KongKonnectProfile;
import com.forgeshift.profile.config.domain.ProfileStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Public representation of a KongKonnectProfile: every field of the document except the
 * personal access token, which exists only server-side and in the discovery, migration and
 * validation services that read it from Mongo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KongKonnectProfileResponse {

    private String id;
    private String companyName;
    private String profileName;
    private String adminUrl;
    /**
     * {@code "***"} when a token is stored. An update that leaves konnectPat out, or sends this
     * back, keeps the stored token.
     */
    private String konnectPatStored;
    private String region;
    private List<KongKonnectControlPlane> controlPlanes;
    private boolean defaultProfile;
    private String defaultControlPlane;
    private ProfileStatus status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime lastUpdatedAt;
    private String lastUpdatedBy;

    public static KongKonnectProfileResponse from(KongKonnectProfile p) {
        return KongKonnectProfileResponse.builder()
                .id(p.getId())
                .companyName(p.getCompanyName())
                .profileName(p.getProfileName())
                .adminUrl(p.getAdminUrl())
                .konnectPatStored(SecretMask.of(p.getKonnectPat()))
                .region(p.getRegion())
                .controlPlanes(p.getControlPlanes())
                .defaultProfile(p.isDefaultProfile())
                .defaultControlPlane(p.getDefaultControlPlane())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .createdBy(p.getCreatedBy())
                .lastUpdatedAt(p.getLastUpdatedAt())
                .lastUpdatedBy(p.getLastUpdatedBy())
                .build();
    }
}
