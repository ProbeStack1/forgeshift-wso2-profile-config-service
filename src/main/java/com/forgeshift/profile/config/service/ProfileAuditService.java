package com.forgeshift.profile.config.service;

import com.forgeshift.profile.config.config.ProfileConfigProperties;
import com.forgeshift.profile.config.domain.ProfileAuditLog;
import com.forgeshift.profile.config.repository.ProfileAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Async writer for the profile_audit_log collection. Controllers fire-and-forget
 * one of {@code recordSuccess} / {@code recordFailure} after their work is done.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileAuditService {

    private final ProfileAuditLogRepository repository;
    private final ProfileConfigProperties props;

    @Async
    public void recordSuccess(String provider, String action, String companyName,
                              String profileName, String userEmail,
                              String method, String path, long startMs, int statusCode,
                              Map<String, Object> metadata) {
        if (!props.getAudit().isEnabled()) return;
        try {
            Instant now = Instant.now();
            ProfileAuditLog row = ProfileAuditLog.builder()
                    .companyName(companyName)
                    .provider(provider)
                    .action(action)
                    .profileName(profileName)
                    .userEmail(userEmail)
                    .httpMethod(method)
                    .requestPath(path)
                    .requestedAt(Instant.ofEpochMilli(startMs))
                    .completedAt(now)
                    .elapsedMs(now.toEpochMilli() - startMs)
                    .statusCode(statusCode)
                    .status("SUCCESS")
                    .metadata(metadata)
                    .build();
            repository.save(row);
        } catch (Exception e) {
            log.warn("Failed to write audit row (success): {}", e.getMessage());
        }
    }

    @Async
    public void recordFailure(String provider, String action, String companyName,
                              String profileName, String userEmail,
                              String method, String path, long startMs, int statusCode,
                              String errorMessage) {
        if (!props.getAudit().isEnabled()) return;
        try {
            Instant now = Instant.now();
            ProfileAuditLog row = ProfileAuditLog.builder()
                    .companyName(companyName)
                    .provider(provider)
                    .action(action)
                    .profileName(profileName)
                    .userEmail(userEmail)
                    .httpMethod(method)
                    .requestPath(path)
                    .requestedAt(Instant.ofEpochMilli(startMs))
                    .completedAt(now)
                    .elapsedMs(now.toEpochMilli() - startMs)
                    .statusCode(statusCode)
                    .status("FAILED")
                    .errorMessage(errorMessage)
                    .build();
            repository.save(row);
        } catch (Exception e) {
            log.warn("Failed to write audit row (failure): {}", e.getMessage());
        }
    }
}
