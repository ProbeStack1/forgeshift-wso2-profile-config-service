package com.forgeshift.profile.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class Wso2VerifyResponse {
    private boolean success;
    private String tokenAuthType;          // BEARER / NONE
    private String tokenPrefix;            // first 6 chars only
    private String tenantInfo;             // when includeTenantInfo == true
    private long elapsedMs;
    private Instant verifiedAt;
    private String errorMessage;
}
