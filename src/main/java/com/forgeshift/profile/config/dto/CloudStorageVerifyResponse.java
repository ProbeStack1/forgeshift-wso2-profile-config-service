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
public class CloudStorageVerifyResponse {
    private boolean success;
    private String bucket;
    private String projectId;
    private String clientEmail;
    /** Number of objects sampled (cap = 1) - "1" means write/read works. */
    private int sampleCount;
    private long elapsedMs;
    private Instant verifiedAt;
    private String errorMessage;
}
