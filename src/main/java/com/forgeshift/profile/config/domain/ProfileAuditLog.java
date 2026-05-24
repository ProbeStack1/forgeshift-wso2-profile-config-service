package com.forgeshift.profile.config.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/** One row per REST mutation against this service. Async write. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("profile_audit_log")
public class ProfileAuditLog {

    @Id
    private String id;

    @Indexed
    private String companyName;

    @Indexed
    private String userEmail;

    /** Provider slug: WSO2, KONG_KONNECT, CLOUD_STORAGE, TENANT */
    @Indexed
    private String provider;

    /** Action verb: CREATE, UPDATE, DELETE, VERIFY, READ, READ_LIST */
    @Indexed
    private String action;

    private String profileName;
    private String requestPath;
    private String httpMethod;
    private String remoteIp;

    private Instant requestedAt;
    private Instant completedAt;
    private long elapsedMs;

    private String status;          // SUCCESS / FAILED
    private int statusCode;
    private String errorMessage;

    /** Free-form extras the controller wants to attach. Never include secrets. */
    private Map<String, Object> metadata;
}
