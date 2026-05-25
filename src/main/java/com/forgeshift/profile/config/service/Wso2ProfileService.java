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
import java.util.ArrayList;
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

    /**
     * Create a single profile that owns one WSO2 instance + admin credentials.
     *
     * <p>Order matters: DCR runs first so the tenants discovery call can use
     * an OAuth Bearer token (WSO2 APIM gates {@code /api/server/v1/tenants}
     * behind {@code internal_list_tenants}, which default DCR apps can't
     * request — the client falls back to the APIM DevPortal endpoint, which
     * needs a valid token). The DCR-issued clientId/secret is stored on the
     * profile and reused for every tenant the profile manages.
     *
     * <p>Tenant binding precedence:
     * <ul>
     *   <li>Explicit {@code tenants} list in the request → used as-is.</li>
     *   <li>Otherwise → {@code carbon.super} + every domain WSO2 returns
     *       (deduplicated). If discovery returns nothing the profile binds
     *       to {@code carbon.super} alone.</li>
     * </ul>
     */
    public Wso2ProfileResponse create(Wso2ProfileRequest req) {
        // 1. Reject duplicate (companyName, profileName) up front.
        repository.findByCompanyNameAndProfileName(req.getCompanyName(), req.getProfileName())
                .ifPresent(p -> {
                    throw new IllegalStateException("Profile already exists: " + p.getId());
                });

        // 2. DCR up front so subsequent calls can use Bearer auth.
        String clientId = req.getClientId();
        String clientSecret = req.getClientSecret();
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            Wso2DcrClient.DcrCredentials creds = dcrClient.register(Wso2DcrClient.DcrRequest.builder()
                    .wso2BaseUrl(req.getWso2BaseUrl())
                    .username(req.getUsername())
                    .password(req.getPassword())
                    .clientName(dcrClientName(req.getCompanyName(), req.getProfileName()))
                    .build());
            clientId = creds.getClientId();
            clientSecret = creds.getClientSecret();
            log.info("DCR generated client (clientId prefix={}...)",
                    clientId.length() > 6 ? clientId.substring(0, 6) : clientId);
        }

        // 3. Enumerate tenants on the WSO2 instance.
        Wso2TenantsResponse tenantsResp = tenantsClient.listTenants(Wso2TenantsRequest.builder()
                .wso2BaseUrl(req.getWso2BaseUrl())
                .username(req.getUsername())
                .password(req.getPassword())
                .clientId(clientId)
                .clientSecret(clientSecret)
                .trustSelfSigned(req.isTrustSelfSigned())
                .build());
        List<String> discovered = extractTenantDomains(tenantsResp);
        List<String> tenants = resolveTenants(req.getTenants(), discovered);
        log.info("WSO2 tenant resolution: requested={} discovered={} bound={}",
                req.getTenants(), discovered, tenants);

        // 4. Persist the single profile.
        Wso2Profile p = Wso2Profile.builder()
                .id(compositeId(req.getCompanyName(), req.getProfileName()))
                .companyName(req.getCompanyName())
                .profileName(req.getProfileName())
                .tenants(tenants)
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
                .discoveredTenants(discovered)
                .discoveredTenantsAt(tenantsResp.isSuccess() ? Instant.now() : null)
                .build();
        return Wso2ProfileResponse.from(repository.save(p));
    }

    public Wso2ProfileResponse update(String companyName, String profileName, Wso2ProfileRequest req) {
        Wso2Profile existing = repository
                .findByCompanyNameAndProfileName(companyName, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + profileName));
        existing.setWso2BaseUrl(req.getWso2BaseUrl());
        existing.setUsername(req.getUsername());
        existing.setPassword(req.getPassword());
        if (StringUtils.hasText(req.getClientId())) existing.setClientId(req.getClientId());
        if (StringUtils.hasText(req.getClientSecret())) existing.setClientSecret(req.getClientSecret());
        existing.setTrustSelfSigned(req.isTrustSelfSigned());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        existing.setNotes(req.getNotes());
        if (req.getTenants() != null && !req.getTenants().isEmpty()) {
            existing.setTenants(req.getTenants());
        }
        existing.setLastModifiedBy(req.getUserEmail());
        return Wso2ProfileResponse.from(repository.save(existing));
    }

    public Wso2ProfileResponse get(String companyName, String profileName) {
        Wso2Profile p = repository
                .findByCompanyNameAndProfileName(companyName, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + profileName));
        return Wso2ProfileResponse.from(p);
    }

    public List<Wso2ProfileResponse> list(String companyName, String wso2Tenant) {
        List<Wso2Profile> rows = StringUtils.hasText(wso2Tenant)
                ? repository.findByCompanyNameAndTenants(companyName, wso2Tenant)
                : repository.findByCompanyName(companyName);
        return rows.stream().map(Wso2ProfileResponse::from).collect(Collectors.toList());
    }

    public void delete(String companyName, String profileName) {
        Wso2Profile p = repository
                .findByCompanyNameAndProfileName(companyName, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + profileName));
        repository.deleteById(p.getId());
    }

    /** Verify against a freshly-supplied set of credentials (no persistence). */
    public Wso2VerifyResponse verify(Wso2VerifyRequest req) {
        return verifyClient.verify(req);
    }

    /** Verify against a saved profile. */
    public Wso2VerifyResponse verifySaved(String companyName, String profileName) {
        Wso2Profile p = repository
                .findByCompanyNameAndProfileName(companyName, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + profileName));
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

    private static List<String> extractTenantDomains(Wso2TenantsResponse resp) {
        if (resp == null || resp.getTenants() == null) return Collections.emptyList();
        return resp.getTenants().stream()
                .map(Wso2TenantsResponse.TenantInfo::getDomain)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    /**
     * Decide which tenants this profile binds to.
     * <ul>
     *   <li>Explicit list from the caller wins.</li>
     *   <li>Otherwise → {@code carbon.super} plus every discovered domain
     *       (deduplicated). carbon.super is always present so the super
     *       tenant is never accidentally left without a profile.</li>
     * </ul>
     */
    private static List<String> resolveTenants(List<String> requested, List<String> discovered) {
        if (requested != null && !requested.isEmpty()) {
            return requested.stream().filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        }
        List<String> out = new ArrayList<>();
        out.add("carbon.super");
        if (discovered != null) {
            for (String d : discovered) {
                if (StringUtils.hasText(d) && !out.contains(d)) out.add(d);
            }
        }
        return out;
    }

    private static String compositeId(String companyName, String profileName) {
        return companyName + "|" + profileName;
    }

    /**
     * DCR app name is keyed by (company, profileName) so the same WSO2
     * instance + profile reuses a single DCR registration on subsequent
     * creates.
     */
    private static String dcrClientName(String companyName, String profileName) {
        return ("forgeshift_" + companyName + "_" + profileName).replaceAll("[^A-Za-z0-9_]", "_");
    }
}
