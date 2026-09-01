package com.forgeshift.profile.config.service;

import com.forgeshift.profile.config.client.KongKonnectVerifyClient;
import com.forgeshift.profile.config.domain.KongKonnectControlPlane;
import com.forgeshift.profile.config.domain.KongKonnectProfile;
import com.forgeshift.profile.config.domain.ProfileStatus;
import com.forgeshift.profile.config.dto.KongKonnectProfileRequest;
import com.forgeshift.profile.config.dto.KongKonnectVerifyRequest;
import com.forgeshift.profile.config.dto.KongKonnectVerifyResponse;
import com.forgeshift.profile.config.exception.ProfileNotFoundException;
import com.forgeshift.profile.config.repository.KongKonnectProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KongKonnectProfileService {

    private final KongKonnectProfileRepository repository;
    private final KongKonnectVerifyClient verifyClient;

    public KongKonnectProfile create(KongKonnectProfileRequest req) {
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
        return saved;
    }

    public KongKonnectProfile update(String id, KongKonnectProfileRequest req) {
        KongKonnectProfile profile = repository.findByIdAndCompanyNameAndStatus(
                id,
                req.getCompanyName(),
                ProfileStatus.ACTIVE
        ).orElseThrow(() -> new ProfileNotFoundException("Profile not found"));

        List<KongKonnectControlPlane> controlPlanes =
                verifyClient.fetchControlPlanes(req.getRegion(), req.getKonnectPat());

        profile.setProfileName(req.getProfileName());
        profile.setAdminUrl(req.getAdminUrl());
        profile.setKonnectPat(req.getKonnectPat());
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
        return saved;
    }

    /**
     * Makes one profile the company default, demoting whichever held it.
     */
    public KongKonnectProfile setDefault(String id, String companyName, String userEmail) {
        KongKonnectProfile profile = repository.findByIdAndCompanyNameAndStatus(
                id, companyName, ProfileStatus.ACTIVE
        ).orElseThrow(() -> new ProfileNotFoundException("Profile not found"));

        profile.setDefaultProfile(true);
        profile.setLastUpdatedAt(LocalDateTime.now());
        profile.setLastUpdatedBy(userEmail);
        KongKonnectProfile saved = repository.save(profile);
        clearOtherDefaults(saved);
        return saved;
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

    public KongKonnectProfile get(String id, String companyName) {
        return repository.findByIdAndCompanyNameAndStatus(
                id,
                companyName,
                ProfileStatus.ACTIVE
        ).orElseThrow(() -> new ProfileNotFoundException("Profile not found"));
    }

    public List<KongKonnectProfile> getAll(String companyName) {
        return repository.findAllByCompanyNameAndStatus(
                companyName,
                ProfileStatus.ACTIVE
        );
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
