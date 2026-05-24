package com.forgeshift.profile.config.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * One row per partner/tenant served by this service.
 *
 * Resolved from the X-Partner-Id header by TenantInterceptor. Holds tenant
 * display name, region, feature flags - anything that the UI needs to
 * render or scope to the tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("tenant_configurations")
public class TenantConfiguration {

    @Id
    private String id;                  // == tenantId, == X-Partner-Id value

    @Indexed(unique = true)
    private String tenantId;

    private String displayName;
    private String region;

    private boolean enabled;

    /** Free-form per-tenant config. */
    private Map<String, Object> properties;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}
