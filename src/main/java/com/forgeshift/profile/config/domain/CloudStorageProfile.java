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
 * Per-(companyName, profileName) Google Cloud Storage profile.
 *
 * Stores the parsed metadata of a GCP service-account JSON key
 * (projectId, clientEmail, etc.) plus the encoded SA JSON itself
 * (kept inline in MongoDB - small enough; can be moved to KMS later).
 *
 * Used by the migrator and discovery services to read/write artifacts in GCS.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("cloud_storage_profiles")
@CompoundIndexes({
        @CompoundIndex(name = "idx_company_profile",
                def = "{'companyName': 1, 'profileName': 1}", unique = true)
})
public class CloudStorageProfile {

    @Id
    private String id;

    private String companyName;
    private String profileName;

    /** Target GCS bucket name. */
    private String bucket;

    /** GCP project id (parsed from the SA JSON for verification). */
    private String projectId;

    /** Service account email (parsed from the SA JSON). */
    private String clientEmail;

    /** Private key id (parsed). Not the key itself. */
    private String privateKeyId;

    /**
     * Full service-account JSON content, base64-encoded.
     * Stored in plain text for the MVP - move to a secret store later.
     */
    private String serviceAccountJsonBase64;

    /** Optional path prefix inside the bucket where all writes land. */
    private String objectPrefix;

    private String notes;
    private String createdBy;
    private String lastModifiedBy;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    private Instant lastVerifiedAt;
    private String lastVerifiedDetail;
}
