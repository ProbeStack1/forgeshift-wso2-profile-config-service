package com.forgeshift.profile.config.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All tunables for the profile-config service.
 * Bound from {@code forgeshift.profile-config.*}.
 */
@Data
@ConfigurationProperties(prefix = "forgeshift.profile-config")
public class ProfileConfigProperties {

    /** Mongo collection holding WSO2 connection profiles. Shared with the discovery service. */
    private String wso2ProfilesCollection = "profiles";

    private String kongKonnectProfilesCollection = "kong_konnect_profiles";
    private String cloudStorageProfilesCollection = "cloud_storage_profiles";
    private String auditCollection = "profile_audit_log";
    private String tenantConfigCollection = "tenant_configurations";

    private Verify verify = new Verify();
    private Tenant tenant = new Tenant();
    private Audit audit = new Audit();

    @Data
    public static class Verify {
        private boolean wso2IncludeTenantInfo = true;
        private int timeoutSeconds = 15;
    }

    @Data
    public static class Tenant {
        private String headerName = "X-Partner-Id";
        private String defaultTenant = "probestack";
    }

    @Data
    public static class Audit {
        private boolean enabled = true;
    }
}
