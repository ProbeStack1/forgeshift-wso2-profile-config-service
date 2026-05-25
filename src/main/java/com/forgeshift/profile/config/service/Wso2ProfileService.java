package com.forgeshift.profile.config.service;

import com.forgeshift.profile.config.client.Wso2DcrClient;
import com.forgeshift.profile.config.client.Wso2TenantsClient;
import com.forgeshift.profile.config.client.Wso2VerifyClient;
import com.forgeshift.profile.config.domain.Wso2Profile;
import com.forgeshift.profile.config.dto.Wso2ProfileRequest;
import com.forgeshift.profile.config.dto.Wso2ProfileResponse;
import com.forgeshift.profile.config.dto.Wso2TenantsRequest;
import com.forgeshift.profile.config.dto.Wso2TenantsResponse;
import com.forgeshift.profile.config.dto.Wso2VerifyRequest;
import com.forgeshift.profile.config.dto.Wso2VerifyResponse;
import com.forgeshift.profile.config.exception.ProfileNotFoundException;
import com.forgeshift.profile.config.repository.Wso2ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Wso2ProfileService {

    private final Wso2ProfileRepository repository;
    private final Wso2VerifyClient verifyClient;
    private final Wso2DcrClient dcrClient;
    private final Wso2TenantsClient tenantsClient;

    public Wso2ProfileResponse create(Wso2ProfileRequest req) {
        Wso2TenantsResponse tenants = tenantsClient.listTenants(Wso2TenantsRequest.builder()
                .wso2BaseUrl(req.getWso2BaseUrl())
                .username(req.getUsername())
                .password(req.getPassword())
                .trustSelfSigned(req.isTrustSelfSigned())
                .build());
        List<String> tenantDomains = extractTenantDomains(tenants);
        String resolvedTenant = resolveTenant(req.getWso2Tenant(), tenantDomains);
        req.setWso2Tenant(resolvedTenant);

        repository.findByCompanyNameAndWso2TenantAndProfileName(
                req.getCompanyName(), resolvedTenant, req.getProfileName())
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
                .wso2Tenant(resolvedTenant)
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
                .discoveredTenants(tenantDomains)
                .discoveredTenantsAt(tenants.isSuccess() ? Instant.now() : null)
                .build();
        return Wso2ProfileResponse.from(repository.save(p));
    }

    private static List<String> extractTenantDomains(Wso2TenantsResponse resp) {
        if (resp == null || resp.getTenants() == null) return Collections.emptyList();
        return resp.getTenants().stream()
                .map(Wso2TenantsResponse.TenantInfo::getDomain)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    /**
     * Pick the tenant to bind the profile to. Explicit user value wins; otherwise
     * prefer {@code carbon.super} when present, then the first discovered domain,
     * and finally fall back to {@code carbon.super} if discovery returned nothing
     * (e.g. unreachable WSO2 instance at create time).
     */
    private static String resolveTenant(String requested, List<String> discovered) {
        if (StringUtils.hasText(requested)) return requested;
        if (discovered != null && discovered.contains("carbon.super")) return "carbon.super";
        if (discovered != null && !discovered.isEmpty()) return discovered.get(0);
        log.warn("WSO2 tenants discovery returned no domains — defaulting wso2Tenant to carbon.super");
        return "carbon.super";
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
