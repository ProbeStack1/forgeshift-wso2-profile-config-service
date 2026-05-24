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
public class KongKonnectVerifyResponse {
    private boolean success;
    private String controlPlaneName;
    private String controlPlaneRegion;
    private long elapsedMs;
    private Instant verifiedAt;
    private String errorMessage;
}
