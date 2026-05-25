package com.forgeshift.profile.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Wso2TenantsResponse {

    private boolean success;

    /** Number of tenants reported by WSO2 (totalResults from the upstream response). */
    private Integer totalResults;

    private List<TenantInfo> tenants;

    private long elapsedMs;
    private Instant fetchedAt;
    private String errorMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TenantInfo {
        /** Tenant domain — this is the value to use as {@code wso2Tenant}. */
        private String domain;
        private String id;
        private String lifecycleStatus;
        private Boolean active;
    }
}
