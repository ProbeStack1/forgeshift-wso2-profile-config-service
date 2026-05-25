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
 * Per-(companyName, profileName) WSO2 connection profile.
 *
 * <p>One document represents a single WSO2 instance + admin credentials. The
 * tenants it manages are tracked in the {@link #tenants} array — typically
 * {@code carbon.super} plus every domain returned by the WSO2 tenants API
 * during create. The discovery service finds creds for a given tenant by
 * scanning that array (see {@code Wso2TenantProfileService.resolve}).
 *
 * <p><strong>Schema migration:</strong> earlier revisions of this service
 * keyed by {@code (companyName, wso2Tenant, profileName)}. Existing
 * documents must be re-created — the new composite id is incompatible.
 *
 * <p>Secrets are stored in plain text for the MVP. Mask on every read.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("wso2_profiles")
@CompoundIndexes({
        @CompoundIndex(name = "idx_company_profile",
                def = "{'companyName': 1, 'profileName': 1}", unique = true),
        @CompoundIndex(name = "idx_company_tenants",
                def = "{'companyName': 1, 'tenants': 1}")
})
public class Wso2Profile implements Persistable<String> {

    @Id
    private String id;

    private String companyName;
    private String profileName;

    /**
     * Tenant domains this profile manages. Always contains at least
     * {@code carbon.super}; additional entries come from the WSO2 tenants
     * API at create time. The discovery service's resolver matches a
     * requested tenant against this list.
     */
    private List<String> tenants;

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
     * Raw tenant list returned by the WSO2 tenants API at create time
     * (mirrors the upstream response — typically excludes {@code carbon.super}
     * which WSO2 manages implicitly). For the canonical set of tenants this
     * profile actually binds to, see {@link #tenants}.
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
