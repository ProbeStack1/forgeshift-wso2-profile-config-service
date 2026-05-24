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
 * Per-(companyName, wso2Tenant, profileName) WSO2 connection profile.
 *
 * Lives in the {@code profiles} collection — same one the discovery service
 * already reads from via Wso2TenantProfileService. The discovery service's
 * shape uses (companyName, wso2Tenant) as the unique key; this service
 * additionally tracks profileName so a tenant can have multiple named
 * profiles (e.g. "primary" + "readonly").
 *
 * Secrets are stored in plain text for the MVP. Mask on every read.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("profiles")
@CompoundIndexes({
        @CompoundIndex(name = "idx_company_tenant_profile",
                def = "{'companyName': 1, 'wso2Tenant': 1, 'profileName': 1}", unique = true)
})
public class Wso2Profile {

    @Id
    private String id;

    private String companyName;
    private String wso2Tenant;
    private String profileName;

    private String wso2BaseUrl;
    private String username;
    private String password;
    private String clientId;
    private String clientSecret;

    private boolean trustSelfSigned;

    /**
     * Lifecycle status. The discovery service's token resolver only picks
     * profiles where {@code status == ACTIVE} (or where the field is absent,
     * treated as ACTIVE for back-compat with documents written before this
     * field existed). Use INACTIVE to keep a profile around but suspend its
     * use by discoveries.
     */
    private String status;

    private String notes;
    private String createdBy;
    private String lastModifiedBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /** Result of the last successful verify call, if any. */
    private Instant lastVerifiedAt;
    private String lastVerifiedTenantInfo;
}
