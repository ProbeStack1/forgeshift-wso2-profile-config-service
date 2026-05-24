package com.forgeshift.profile.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forgeshift.profile.config.domain.CloudStorageProfile;
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
public class CloudStorageProfileResponse {

    private String id;
    private String companyName;
    private String profileName;
    private String bucket;
    private String projectId;
    private String clientEmail;
    private String privateKeyId;
    /** Always masked. Use the dedicated reveal endpoint (post-MVP) for the raw bytes. */
    private String serviceAccountJsonStored;
    private String objectPrefix;
    private String notes;
    private String createdBy;
    private String lastModifiedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastVerifiedAt;
    private String lastVerifiedDetail;

    public static CloudStorageProfileResponse from(CloudStorageProfile p) {
        return CloudStorageProfileResponse.builder()
                .id(p.getId())
                .companyName(p.getCompanyName())
                .profileName(p.getProfileName())
                .bucket(p.getBucket())
                .projectId(p.getProjectId())
                .clientEmail(p.getClientEmail())
                .privateKeyId(p.getPrivateKeyId())
                .serviceAccountJsonStored(p.getServiceAccountJsonBase64() == null ? null : "***")
                .objectPrefix(p.getObjectPrefix())
                .notes(p.getNotes())
                .createdBy(p.getCreatedBy())
                .lastModifiedBy(p.getLastModifiedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .lastVerifiedAt(p.getLastVerifiedAt())
                .lastVerifiedDetail(p.getLastVerifiedDetail())
                .build();
    }
}
