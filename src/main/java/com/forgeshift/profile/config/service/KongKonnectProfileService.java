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
        profile.setStatus(ProfileStatus.ACTIVE);
        profile.setCreatedAt(now);
        profile.setCreatedBy(req.getUserEmail());
        profile.setLastUpdatedAt(now);
        profile.setLastUpdatedBy(req.getUserEmail());

        return repository.save(profile);
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
        profile.setLastUpdatedAt(LocalDateTime.now());
        profile.setLastUpdatedBy(req.getUserEmail());

        return repository.save(profile);
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

        profile.setStatus(ProfileStatus.INACTIVE);
        profile.setLastUpdatedAt(LocalDateTime.now());
        profile.setLastUpdatedBy(userEmail);

        repository.save(profile);
    }

    public KongKonnectVerifyResponse verifyConnection(KongKonnectVerifyRequest req) {
        return verifyClient.verify(req);
    }
}
