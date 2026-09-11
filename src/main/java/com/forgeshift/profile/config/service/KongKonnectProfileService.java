package com.forgeshift.profile.config.service;

import com.forgeshift.profile.config.client.KongKonnectVerifyClient;
import com.forgeshift.profile.config.domain.KongKonnectControlPlane;
import com.forgeshift.profile.config.domain.KongKonnectProfile;
import com.forgeshift.profile.config.domain.ProfileStatus;
import com.forgeshift.profile.config.dto.KongKonnectProfileRequest;
import com.forgeshift.profile.config.dto.KongKonnectProfileResponse;
import com.forgeshift.profile.config.dto.KongKonnectVerifyRequest;
import com.forgeshift.profile.config.dto.KongKonnectVerifyResponse;
import com.forgeshift.profile.config.dto.SecretMask;
import com.forgeshift.profile.config.exception.ProfileNotFoundException;
import com.forgeshift.profile.config.repository.KongKonnectProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class KongKonnectProfileService {

    private final KongKonnectProfileRepository repository;
    private final KongKonnectVerifyClient verifyClient;

    public KongKonnectProfileResponse create(KongKonnectProfileRequest req) {
        if (!SecretMask.isNewSecret(req.getKonnectPat())) {
            throw new IllegalArgumentException("konnectPat is required, and a masked value is not a token");
        }
        boolean exists = repository.existsByProfileNameAndCompanyNameAndStatus(
                req.getProfileName(),
                req.getCompanyName(),
                ProfileStatus.ACTIVE
        );

        if (exists) {
            throw new IllegalStateException("Active profile already exists for this company");
        }

        List<KongKonnectControlPlane> controlPlanes =
                verifyClient.fetchControlPlanes(req.getRegion(), req.getKonnectPat());
        LocalDateTime now = LocalDateTime.now();

        KongKonnectProfile profile = new KongKonnectProfile();
        profile.setProfileName(req.getProfileName());
        profile.setCompanyName(req.getCompanyName());
        profile.setAdminUrl(req.getAdminUrl());
        profile.setKonnectPat(req.getKonnectPat());
        profile.setRegion(req.getRegion());
        profile.setControlPlanes(controlPlanes);
        profile.setDefaultControlPlane(req.getDefaultControlPlane());
        // The first profile a company creates is the one everything uses, so it
        // becomes the default without the user having to know the concept.
        boolean isFirstProfile = repository.findAllByCompanyNameAndStatus(
                req.getCompanyName(), ProfileStatus.ACTIVE).isEmpty();
        profile.setDefaultProfile(Boolean.TRUE.equals(req.getDefaultProfile()) || isFirstProfile);
        profile.setStatus(ProfileStatus.ACTIVE);
        profile.setCreatedAt(now);
        profile.setCreatedBy(req.getUserEmail());
        profile.setLastUpdatedAt(now);
        profile.setLastUpdatedBy(req.getUserEmail());

        KongKonnectProfile saved = repository.save(profile);
        if (saved.isDefaultProfile()) {
            clearOtherDefaults(saved);
        }
        return KongKonnectProfileResponse.from(saved);
    }

    public KongKonnectProfileResponse update(String id, KongKonnectProfileRequest req) {
        KongKonnectProfile profile = repository.findByIdAndCompanyNameAndStatus(
                id,
                req.getCompanyName(),
                ProfileStatus.ACTIVE
        ).orElseThrow(() -> new ProfileNotFoundException("Profile not found"));

        String konnectPat = konnectPatFor(profile, req);
        List<KongKonnectControlPlane> controlPlanes =
                verifyClient.fetchControlPlanes(req.getRegion(), konnectPat);

        profile.setProfileName(req.getProfileName());
        profile.setAdminUrl(req.getAdminUrl());
        profile.setKonnectPat(konnectPat);
        profile.setRegion(req.getRegion());
        profile.setControlPlanes(controlPlanes);
        profile.setDefaultControlPlane(req.getDefaultControlPlane());
        if (req.getDefaultProfile() != null) {
            profile.setDefaultProfile(req.getDefaultProfile());
        }
        profile.setLastUpdatedAt(LocalDateTime.now());
        profile.setLastUpdatedBy(req.getUserEmail());

        KongKonnectProfile saved = repository.save(profile);
        if (saved.isDefaultProfile()) {
            clearOtherDefaults(saved);
        }
        return KongKonnectProfileResponse.from(saved);
    }

    /**
     * The token to list control planes with and to save. A request without one - left out,
     * blank, or the mask a read returns - keeps the stored token, as long as adminUrl stays put.
     * Discovery, migration and validation send the token to adminUrl, so moving it without the
     * token would let anyone who can call this endpoint collect a company's token on a server of
     * their own. Region may change: it only ever picks a {@code {region}.api.konghq.com} host.
     */
    private static String konnectPatFor(KongKonnectProfile profile, KongKonnectProfileRequest req) {
        if (SecretMask.isNewSecret(req.getKonnectPat())) {
            return req.getKonnectPat();
        }
        if (!StringUtils.hasText(profile.getKonnectPat())) {
            throw new IllegalArgumentException("konnectPat is required: this profile has no stored token");
        }
        if (!sameUrl(profile.getAdminUrl(), req.getAdminUrl())) {
            throw new IllegalArgumentException("konnectPat is required when adminUrl changes");
        }
        return profile.getKonnectPat();
    }

    /** Same URL, ignoring case, surrounding blanks and trailing slashes. */
    private static boolean sameUrl(String stored, String requested) {
        return normalizeUrl(stored).equals(normalizeUrl(requested));
    }

    private static String normalizeUrl(String url) {
        String normalized = url == null ? "" : url.strip().toLowerCase(Locale.ROOT);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * Makes one profile the company default, demoting whichever held it.
     */
    public KongKonnectProfileResponse setDefault(String id, String companyName, String userEmail) {
        KongKonnectProfile profile = repository.findByIdAndCompanyNameAndStatus(
                id, companyName, ProfileStatus.ACTIVE
        ).orElseThrow(() -> new ProfileNotFoundException("Profile not found"));

        profile.setDefaultProfile(true);
        profile.setLastUpdatedAt(LocalDateTime.now());
        profile.setLastUpdatedBy(userEmail);
        KongKonnectProfile saved = repository.save(profile);
        clearOtherDefaults(saved);
        return KongKonnectProfileResponse.from(saved);
    }

    /**
     * Exactly one default per company: demote every other active profile.
     */
    private void clearOtherDefaults(KongKonnectProfile chosen) {
        List<KongKonnectProfile> others = repository.findAllByCompanyNameAndStatus(
                chosen.getCompanyName(), ProfileStatus.ACTIVE);
        for (KongKonnectProfile other : others) {
            if (!other.getId().equals(chosen.getId()) && other.isDefaultProfile()) {
                other.setDefaultProfile(false);
                repository.save(other);
            }
        }
    }

    public KongKonnectProfileResponse get(String id, String companyName) {
        return repository.findByIdAndCompanyNameAndStatus(
                id,
                companyName,
                ProfileStatus.ACTIVE
        ).map(KongKonnectProfileResponse::from)
                .orElseThrow(() -> new ProfileNotFoundException("Profile not found"));
    }

    public List<KongKonnectProfileResponse> getAll(String companyName) {
        return repository.findAllByCompanyNameAndStatus(
                companyName,
                ProfileStatus.ACTIVE
        ).stream().map(KongKonnectProfileResponse::from).toList();
    }

    public void delete(String id, String companyName, String userEmail) {
        KongKonnectProfile profile = repository.findByIdAndCompanyNameAndStatus(
                id,
                companyName,
                ProfileStatus.ACTIVE
        ).orElseThrow(() -> new ProfileNotFoundException("Profile not found"));

        boolean wasDefault = profile.isDefaultProfile();
        profile.setStatus(ProfileStatus.INACTIVE);
        profile.setDefaultProfile(false);
        profile.setLastUpdatedAt(LocalDateTime.now());
        profile.setLastUpdatedBy(userEmail);

        repository.save(profile);

        // Removing the default would otherwise leave the company with none, and
        // every resolver would fall back to static config.
        if (wasDefault) {
            repository.findAllByCompanyNameAndStatus(companyName, ProfileStatus.ACTIVE)
                    .stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setDefaultProfile(true);
                        next.setLastUpdatedAt(LocalDateTime.now());
                        next.setLastUpdatedBy(userEmail);
                        repository.save(next);
                    });
        }
    }

    public KongKonnectVerifyResponse verifyConnection(KongKonnectVerifyRequest req) {
        return verifyClient.verify(req);
    }
}
