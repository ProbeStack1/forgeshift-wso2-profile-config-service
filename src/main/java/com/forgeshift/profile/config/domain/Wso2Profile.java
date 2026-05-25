package com.forgeshift.profile.config.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

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
@Document("wso2_profiles")
@CompoundIndexes({
        @CompoundIndex(name = "idx_company_tenant_profile",
                def = "{'companyName': 1, 'wso2Tenant': 1, 'profileName': 1}", unique = true)
})
public class Wso2Profile implements Persistable<String> {

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

    /**
     * Tenant domains visible to this profile's admin credentials, captured by
     * calling {@code GET /api/server/v1/tenants} during create. Lets callers
     * see which tenants the WSO2 instance hosts without re-querying.
     */
    private List<String> discoveredTenants;

    /** When {@link #discoveredTenants} was last refreshed. */
    private Instant discoveredTenantsAt;

    /**
     * Tells Spring Data Mongo whether this document is new so {@code @CreatedDate}
     * fires. Without this, the composite {@code id} being populated before save()
     * makes Spring treat every save as an update and skip the create timestamp.
     */
    @Override
    @JsonIgnore
    @Transient
    public boolean isNew() {
        return createdAt == null;
    }
}
