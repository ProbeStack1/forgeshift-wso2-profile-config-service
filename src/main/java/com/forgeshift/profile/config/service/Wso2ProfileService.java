package com.forgeshift.profile.config.service;

import com.forgeshift.profile.config.client.Wso2DcrClient;
import com.forgeshift.profile.config.client.Wso2VerifyClient;
import com.forgeshift.profile.config.domain.Wso2Profile;
import com.forgeshift.profile.config.dto.Wso2ProfileRequest;
import com.forgeshift.profile.config.dto.Wso2ProfileResponse;
import com.forgeshift.profile.config.dto.Wso2VerifyRequest;
import com.forgeshift.profile.config.dto.Wso2VerifyResponse;
import com.forgeshift.profile.config.exception.ProfileNotFoundException;
import com.forgeshift.profile.config.repository.Wso2ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Wso2ProfileService {

    private final Wso2ProfileRepository repository;
    private final Wso2VerifyClient verifyClient;
    private final Wso2DcrClient dcrClient;

    public Wso2ProfileResponse create(Wso2ProfileRequest req) {
        repository.findByCompanyNameAndWso2TenantAndProfileName(
                req.getCompanyName(), req.getWso2Tenant(), req.getProfileName())
                .ifPresent(p -> {
                    throw new IllegalStateException("Profile already exists: " + p.getId());
                });

        String clientId = req.getClientId();
        String clientSecret = req.getClientSecret();
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            Wso2DcrClient.DcrCredentials creds = dcrClient.register(Wso2DcrClient.DcrRequest.builder()
                    .wso2BaseUrl(req.getWso2BaseUrl())
                    .username(req.getUsername())
                    .password(req.getPassword())
                    .clientName(dcrClientName(req))
                    .build());
            clientId = creds.getClientId();
            clientSecret = creds.getClientSecret();
            log.info("DCR generated client for {} (clientId prefix={}...)",
                    compositeId(req),
                    clientId.length() > 6 ? clientId.substring(0, 6) : clientId);
        }

        Wso2Profile p = Wso2Profile.builder()
                .id(compositeId(req))
                .companyName(req.getCompanyName())
                .wso2Tenant(req.getWso2Tenant())
                .profileName(req.getProfileName())
                .wso2BaseUrl(req.getWso2BaseUrl())
                .username(req.getUsername())
                .password(req.getPassword())
                .clientId(clientId)
                .clientSecret(clientSecret)
                .trustSelfSigned(req.isTrustSelfSigned())
                .status(req.getStatus() != null ? req.getStatus() : "ACTIVE")
                .notes(req.getNotes())
                .createdBy(req.getUserEmail())
                .lastModifiedBy(req.getUserEmail())
                .build();
        return Wso2ProfileResponse.from(repository.save(p));
    }

    public Wso2ProfileResponse update(String id, Wso2ProfileRequest req) {
        Wso2Profile existing = repository.findById(id)
                .orElseThrow(() -> new ProfileNotFoundException("Profile not found: " + id));
        existing.setWso2BaseUrl(req.getWso2BaseUrl());
        existing.setUsername(req.getUsername());
        existing.setPassword(req.getPassword());
        existing.setClientId(req.getClientId());
        existing.setClientSecret(req.getClientSecret());
        existing.setTrustSelfSigned(req.isTrustSelfSigned());
        if (req.getStatus() != null) {
            existing.setStatus(req.getStatus());
        }
        existing.setNotes(req.getNotes());
        existing.setLastModifiedBy(req.getUserEmail());
        return Wso2ProfileResponse.from(repository.save(existing));
    }

    public Wso2ProfileResponse get(String companyName, String wso2Tenant, String profileName) {
        Wso2Profile p = repository
                .findByCompanyNameAndWso2TenantAndProfileName(companyName, wso2Tenant, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + wso2Tenant + "|" + profileName));
        return Wso2ProfileResponse.from(p);
    }

    public List<Wso2ProfileResponse> list(String companyName, String wso2Tenant) {
        List<Wso2Profile> rows = (wso2Tenant != null && !wso2Tenant.isBlank())
                ? repository.findByCompanyNameAndWso2Tenant(companyName, wso2Tenant)
                : repository.findByCompanyName(companyName);
        return rows.stream().map(Wso2ProfileResponse::from).collect(Collectors.toList());
    }

    public void delete(String companyName, String wso2Tenant, String profileName) {
        Wso2Profile p = repository
                .findByCompanyNameAndWso2TenantAndProfileName(companyName, wso2Tenant, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + wso2Tenant + "|" + profileName));
        repository.deleteById(p.getId());
    }

    /** Verify against a freshly-supplied set of credentials (no persistence). */
    public Wso2VerifyResponse verify(Wso2VerifyRequest req) {
        return verifyClient.verify(req);
    }

    /** Verify against a saved profile. */
    public Wso2VerifyResponse verifySaved(String companyName, String wso2Tenant, String profileName) {
        Wso2Profile p = repository
                .findByCompanyNameAndWso2TenantAndProfileName(companyName, wso2Tenant, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + wso2Tenant + "|" + profileName));
        Wso2VerifyResponse resp = verifyClient.verify(Wso2VerifyRequest.builder()
                .wso2BaseUrl(p.getWso2BaseUrl())
                .username(p.getUsername())
                .password(p.getPassword())
                .clientId(p.getClientId())
                .clientSecret(p.getClientSecret())
                .trustSelfSigned(p.isTrustSelfSigned())
                .build());

        if (resp.isSuccess()) {
            p.setLastVerifiedAt(Instant.now());
            p.setLastVerifiedTenantInfo(resp.getTenantInfo());
            repository.save(p);
        }
        return resp;
    }

    private static String compositeId(Wso2ProfileRequest req) {
        return req.getCompanyName() + "|" + req.getWso2Tenant() + "|" + req.getProfileName();
    }

    private static String dcrClientName(Wso2ProfileRequest req) {
        return ("forgeshift_" + req.getCompanyName() + "_" + req.getWso2Tenant()
                + "_" + req.getProfileName()).replaceAll("[^A-Za-z0-9_]", "_");
    }
}
