package com.forgeshift.profile.config.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Per-(companyName, profileName) Kong Konnect connection profile.
 *
 * Lives in {@code kong_konnect_profiles}. The migrator service reads this
 * collection at deploy time to find the bearer token + control plane id
 * for the tenant it's migrating into.
 *
 * Secret (kongAccessToken / PAT) is stored in plain text for the MVP.
 * Mask on every read.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("kong_konnect_profiles")
@CompoundIndexes({
        @CompoundIndex(name = "idx_company_profile",
                def = "{'companyName': 1, 'profileName': 1}", unique = true)
})
public class KongKonnectProfile {

    @Id
    private String id;

    private String companyName;
    private String profileName;

    /**
     * Kong Konnect base URL, e.g. https://us.api.konghq.com
     * Region (us|eu|au) is encoded in the host.
     */
    private String konnectBaseUrl;

    /** Personal Access Token (kpat_...) used as Bearer in every Konnect call. */
    private String konnectAccessToken;

    /** Konnect control plane UUID this profile targets. */
    private String controlPlaneId;

    /** Free-form region label for UI purposes, e.g. "us", "eu". */
    private String region;

    private String notes;
    private String createdBy;
    private String lastModifiedBy;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    private Instant lastVerifiedAt;
    private String lastVerifiedControlPlaneName;
}
