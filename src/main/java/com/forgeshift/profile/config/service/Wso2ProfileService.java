package com.forgeshift.profile.config.service;

import com.forgeshift.profile.config.client.Wso2DcrClient;
import com.forgeshift.profile.config.client.Wso2TenantsClient;
import com.forgeshift.profile.config.client.Wso2VerifyClient;
import com.forgeshift.profile.config.domain.Wso2Profile;
import com.forgeshift.profile.config.dto.Wso2ProfileInfoRequest;
import com.forgeshift.profile.config.dto.Wso2ProfileInfoResponse;
import com.forgeshift.profile.config.dto.Wso2ProfileRequest;
import com.forgeshift.profile.config.dto.Wso2ProfileResponse;
import com.forgeshift.profile.config.dto.Wso2TenantsRequest;
import com.forgeshift.profile.config.dto.Wso2TenantsResponse;
import com.forgeshift.profile.config.dto.Wso2VerifyRequest;
import com.forgeshift.profile.config.dto.Wso2VerifyResponse;
import com.forgeshift.profile.config.exception.ProfileNotFoundException;
import com.forgeshift.profile.config.repository.Wso2ProfileRepository;
import com.forgeshift.profile.config.validator.Wso2RequestValidator;
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
    private final Wso2RequestValidator validator;

    /**
     * Probe-only flow for {@code POST /wso2/profiles/info}: does the same
     * DCR + tenants-discovery work that {@link #create} performs, but
     * <strong>never writes to the database</strong>. The frontend uses
     * the returned {@code discoveredTenants} to show a tenant-picker and
     * then calls {@link #create} with the user's chosen
     * {@code defaultWso2Tenant}.
     */
    public Wso2ProfileInfoResponse info(Wso2ProfileInfoRequest req) {
        validator.validateInfoRequest(req);
        try {
            // DCR (idempotent on clientName) so we get a token to call the
            // APIM DevPortal tenants endpoint — same path the save flow
            // uses, so the list returned here matches what save sees.
            String clientId = req.getClientId();
            String clientSecret = req.getClientSecret();
            if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
                // profileName is optional at the info stage. When the user
                // already knows the profile name they'll save to, derive the
                // stable DCR app from it so the follow-up save call reuses
                // the same OAuth client. Otherwise fall back to a generic
                // per-company "info_probe" name.
                String dcrName = StringUtils.hasText(req.getProfileName())
                        ? dcrClientName(req.getCompanyName(), req.getProfileName())
                        : dcrClientName(req.getCompanyName(), "info_probe");
                Wso2DcrClient.DcrCredentials creds = dcrClient.register(Wso2DcrClient.DcrRequest.builder()
                        .wso2BaseUrl(req.getWso2BaseUrl())
                        .username(req.getUsername())
                        .password(req.getPassword())
                        .clientName(dcrName)
                        .build());
                clientId = creds.getClientId();
                clientSecret = creds.getClientSecret();
            }

            Wso2TenantsResponse tenantsResp = tenantsClient.listTenants(Wso2TenantsRequest.builder()
                    .wso2BaseUrl(req.getWso2BaseUrl())
                    .username(req.getUsername())
                    .password(req.getPassword())
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .trustSelfSigned(req.isTrustSelfSigned())
                    .build());
            List<String> discovered = extractTenantDomains(tenantsResp);
            log.info("Info probe (company={} profile={}): discovered tenants={}",
                    req.getCompanyName(), req.getProfileName(), discovered);

            return Wso2ProfileInfoResponse.builder()
                    .success(tenantsResp.isSuccess())
                    .errorMessage(tenantsResp.getErrorMessage())
                    .companyName(req.getCompanyName())
                    .profileName(req.getProfileName())
                    .wso2BaseUrl(req.getWso2BaseUrl())
                    .username(req.getUsername())
                    .trustSelfSigned(req.isTrustSelfSigned())
                    .notes(req.getNotes())
                    .userEmail(req.getUserEmail())
                    .discoveredTenants(discovered)
                    .discoveredTenantsAt(tenantsResp.isSuccess() ? Instant.now() : null)
                    .build();
        } catch (Exception e) {
            log.warn("Info probe failed for company={} profile={}: {}",
                    req.getCompanyName(), req.getProfileName(), e.getMessage());
            return Wso2ProfileInfoResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .companyName(req.getCompanyName())
                    .profileName(req.getProfileName())
                    .wso2BaseUrl(req.getWso2BaseUrl())
                    .username(req.getUsername())
                    .trustSelfSigned(req.isTrustSelfSigned())
                    .notes(req.getNotes())
                    .userEmail(req.getUserEmail())
                    .build();
        }
    }

    /**
     * Persist a single profile that owns one WSO2 instance + admin credentials.
     *
     * <p>Called by {@code POST /wso2/profiles/save}. The caller must
     * supply the {@code defaultWso2Tenant} the user picked from the
     * {@link #info} call's discovered list — the profile binds to that
     * single tenant. The full discovered list is still captured on the
     * row's {@code discoveredTenants} field as a snapshot.
     */
    public Wso2ProfileResponse create(Wso2ProfileRequest req) {
        validator.validateCreateRequest(req);

        // 1. Reject duplicate (companyName, profileName) up front.
        repository.findByCompanyNameAndProfileName(req.getCompanyName(), req.getProfileName())
                .ifPresent(p -> {
                    throw new IllegalStateException("Profile already exists: " + p.getId());
                });
        validator.validateUniqueWso2Config(
                repository.findByCompanyName(req.getCompanyName()),
                req.getWso2BaseUrl(),
                req.getDefaultWso2Tenant(),
                null);

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

        // 3. Re-discover tenants so the snapshot on the row reflects what
        //    actually exists at save time (the user may have picked
        //    defaultWso2Tenant minutes ago).
        Wso2TenantsResponse tenantsResp = tenantsClient.listTenants(Wso2TenantsRequest.builder()
                .wso2BaseUrl(req.getWso2BaseUrl())
                .username(req.getUsername())
                .password(req.getPassword())
                .clientId(clientId)
                .clientSecret(clientSecret)
                .trustSelfSigned(req.isTrustSelfSigned())
                .build());
        List<String> discovered = extractTenantDomains(tenantsResp);
        log.info("WSO2 tenant binding: defaultWso2Tenant={} discoveredAtSave={}",
                req.getDefaultWso2Tenant(), discovered);

        // 4. Persist the single profile.
        Wso2Profile p = Wso2Profile.builder()
                .id(compositeId(req.getCompanyName(), req.getProfileName()))
                .companyName(req.getCompanyName())
                .profileName(req.getProfileName())
                .defaultWso2Tenant(req.getDefaultWso2Tenant())
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
        validator.validateUpdateRequest(req);
        final String normalizedCompanyName = validator.normalizeRequiredCompanyName(companyName);
        final String normalizedProfileName = validator.normalizeRequiredProfileName(profileName);

        Wso2Profile existing = repository
                .findByCompanyNameAndProfileName(normalizedCompanyName, normalizedProfileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + normalizedCompanyName + "|" + normalizedProfileName));
        String targetTenant = StringUtils.hasText(req.getDefaultWso2Tenant())
                ? req.getDefaultWso2Tenant()
                : existing.getDefaultWso2Tenant();
        validator.validateUniqueWso2Config(
                repository.findByCompanyName(normalizedCompanyName),
                req.getWso2BaseUrl(),
                targetTenant,
                existing.getId());
        existing.setWso2BaseUrl(req.getWso2BaseUrl());
        existing.setUsername(req.getUsername());
        existing.setPassword(req.getPassword());
        if (StringUtils.hasText(req.getClientId())) existing.setClientId(req.getClientId());
        if (StringUtils.hasText(req.getClientSecret())) existing.setClientSecret(req.getClientSecret());
        existing.setTrustSelfSigned(req.isTrustSelfSigned());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        existing.setNotes(req.getNotes());
        if (StringUtils.hasText(req.getDefaultWso2Tenant())) {
            existing.setDefaultWso2Tenant(req.getDefaultWso2Tenant());
        }
        existing.setLastModifiedBy(req.getUserEmail());
        return Wso2ProfileResponse.from(repository.save(existing));
    }

    public Wso2ProfileResponse get(String companyName, String profileName) {
        final String normalizedCompanyName = validator.normalizeRequiredCompanyName(companyName);
        final String normalizedProfileName = validator.normalizeRequiredProfileName(profileName);
        Wso2Profile p = repository
                .findByCompanyNameAndProfileName(normalizedCompanyName, normalizedProfileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + normalizedCompanyName + "|" + normalizedProfileName));
        return Wso2ProfileResponse.from(p);
    }

    public List<Wso2ProfileResponse> list(String companyName, String wso2Tenant) {
        String normalizedCompanyName = validator.normalizeRequiredCompanyName(companyName);
        String normalizedTenant = validator.normalizeOptionalTenant(wso2Tenant);
        List<Wso2Profile> rows = StringUtils.hasText(normalizedTenant)
                ? repository.findByCompanyNameAndDefaultWso2Tenant(normalizedCompanyName, normalizedTenant)
                : repository.findByCompanyName(normalizedCompanyName);
        return rows.stream().map(Wso2ProfileResponse::from).collect(Collectors.toList());
    }

    public void delete(String companyName, String profileName) {
        final String normalizedCompanyName = validator.normalizeRequiredCompanyName(companyName);
        final String normalizedProfileName = validator.normalizeRequiredProfileName(profileName);
        Wso2Profile p = repository
                .findByCompanyNameAndProfileName(normalizedCompanyName, normalizedProfileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + normalizedCompanyName + "|" + normalizedProfileName));
        repository.deleteById(p.getId());
    }

    /**
     * Verify against a freshly-supplied set of credentials (no persistence).
     *
     * <p>If {@code clientId}/{@code clientSecret} are missing, DCR is run
     * first (idempotent on {@code clientName}) and the generated pair is
     * used for the password-grant token call. Lets callers verify with
     * just {@code wso2BaseUrl + username + password} when they don't
     * already have a DCR client to hand.
     */
    public Wso2VerifyResponse verify(Wso2VerifyRequest req) {
        validator.validateVerifyRequest(req);
        if (!StringUtils.hasText(req.getClientId()) || !StringUtils.hasText(req.getClientSecret())) {
            String clientName = StringUtils.hasText(req.getCompanyName()) && StringUtils.hasText(req.getProfileName())
                    ? dcrClientName(req.getCompanyName(), req.getProfileName())
                    : "forgeshift_verify";
            Wso2DcrClient.DcrCredentials creds = dcrClient.register(Wso2DcrClient.DcrRequest.builder()
                    .wso2BaseUrl(req.getWso2BaseUrl())
                    .username(req.getUsername())
                    .password(req.getPassword())
                    .clientName(clientName)
                    .build());
            req.setClientId(creds.getClientId());
            req.setClientSecret(creds.getClientSecret());
            log.info("DCR-generated client for verify (clientName={}, clientId prefix={}...)",
                    clientName, creds.getClientId().length() > 6
                            ? creds.getClientId().substring(0, 6) : creds.getClientId());
        }
        return verifyClient.verify(req);
    }

    /** Verify against a saved profile. */
    public Wso2VerifyResponse verifySaved(String companyName, String profileName) {
        final String normalizedCompanyName = validator.normalizeRequiredCompanyName(companyName);
        final String normalizedProfileName = validator.normalizeRequiredProfileName(profileName);
        Wso2Profile p = repository
                .findByCompanyNameAndProfileName(normalizedCompanyName, normalizedProfileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + normalizedCompanyName + "|" + normalizedProfileName));
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
