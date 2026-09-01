package com.forgeshift.profile.config.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Per-(companyName, profileName) Kong Konnect connection profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("kong_konnect_profiles")
@CompoundIndexes({
        @CompoundIndex(name = "unique_active_profile_per_company",
                def = "{'profileName': 1, 'companyName': 1, 'status': 1}", unique = true)
})
public class KongKonnectProfile {

    @Id
    private String id;

    private String companyName;
    private String profileName;
    private String adminUrl;
    private String konnectPat;
    private String region;
    private List<KongKonnectControlPlane> controlPlanes;
    /**
     * The profile consumers use when a request does not name one. A company can
     * hold several active profiles; without this there is nothing to say which
     * is the live one, and resolvers fall through to static config.
     */
    private boolean defaultProfile;
    /**
     * Control plane to use when a request does not name one. The config UI has
     * always sent this; the field it was posted into did not exist, so it was
     * silently dropped on every save.
     */
    private String defaultControlPlane;
    private ProfileStatus status;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime lastUpdatedAt;
    private String lastUpdatedBy;
}
