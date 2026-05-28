package com.forgeshift.profile.config.service;

import com.forgeshift.profile.config.client.KongKonnectVerifyClient;
import com.forgeshift.profile.config.domain.KongKonnectControlPlane;
import com.forgeshift.profile.config.domain.KongKonnectProfile;
import com.forgeshift.profile.config.domain.ProfileStatus;
import com.forgeshift.profile.config.dto.KongKonnectProfileRequest;
import com.forgeshift.profile.config.dto.KongKonnectProfileResponse;
import com.forgeshift.profile.config.dto.KongKonnectVerifyRequest;
import com.forgeshift.profile.config.dto.KongKonnectVerifyResponse;
import com.forgeshift.profile.config.exception.ProfileNotFoundException;
import com.forgeshift.profile.config.repository.KongKonnectProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KongKonnectProfileService {

    private final KongKonnectProfileRepository repository;
    private final KongKonnectVerifyClient verifyClient;

    public KongKonnectProfileResponse create(KongKonnectProfileRequest req) {
        repository.findByCompanyNameAndProfileName(req.getCompanyName(), req.getProfileName())
                .filter(this::isActive)
                .ifPresent(p -> { throw new IllegalStateException("Active profile already exists for this company"); });

        List<KongKonnectControlPlane> controlPlanes =
                verifyClient.fetchControlPlanes(req.getAdminUrl(), req.getKonnectPat());
        LocalDateTime now = LocalDateTime.now();

        KongKonnectProfile p = KongKonnectProfile.builder()
                .companyName(req.getCompanyName())
                .profileName(req.getProfileName())
                .adminUrl(req.getAdminUrl())
                .konnectPat(req.getKonnectPat())
                .region(req.getRegion())
                .controlPlanes(controlPlanes)
                .status(ProfileStatus.ACTIVE)
                .createdAt(now)
                .createdBy(req.getUserEmail())
                .lastUpdatedAt(now)
                .lastUpdatedBy(req.getUserEmail())
                .build();
        return KongKonnectProfileResponse.from(repository.save(p));
    }

    public KongKonnectProfileResponse update(String id, KongKonnectProfileRequest req) {
        KongKonnectProfile p = findActiveById(id, req.getCompanyName())
                .orElseThrow(() -> new ProfileNotFoundException("Profile not found: " + id));

        List<KongKonnectControlPlane> controlPlanes =
                verifyClient.fetchControlPlanes(req.getAdminUrl(), req.getKonnectPat());

        p.setProfileName(req.getProfileName());
        p.setAdminUrl(req.getAdminUrl());
        p.setKonnectPat(req.getKonnectPat());
        p.setRegion(req.getRegion());
        p.setControlPlanes(controlPlanes);
        p.setLastUpdatedAt(LocalDateTime.now());
        p.setLastUpdatedBy(req.getUserEmail());
        return KongKonnectProfileResponse.from(repository.save(p));
    }

    public KongKonnectProfileResponse getById(String id, String companyName) {
        return KongKonnectProfileResponse.from(findActiveById(id, companyName)
                .orElseThrow(() -> new ProfileNotFoundException("Profile not found: " + id)));
    }

    public KongKonnectProfileResponse getByProfileName(String companyName, String profileName) {
        return KongKonnectProfileResponse.from(repository
                .findByCompanyNameAndProfileName(companyName, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + profileName)));
    }

    public List<KongKonnectProfileResponse> list(String companyName) {
        return repository.findByCompanyName(companyName).stream()
                .filter(this::isActive)
                .map(KongKonnectProfileResponse::from)
                .collect(Collectors.toList());
    }

    public void delete(String id, String companyName, String userEmail) {
        KongKonnectProfile p = findActiveById(id, companyName)
                .orElseThrow(() -> new ProfileNotFoundException("Profile not found: " + id));
        p.setStatus(ProfileStatus.INACTIVE);
        p.setLastUpdatedAt(LocalDateTime.now());
        p.setLastUpdatedBy(userEmail);
        repository.save(p);
    }

    public void delete(String companyName, String profileName) {
        KongKonnectProfile p = repository.findByCompanyNameAndProfileName(companyName, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + profileName));
        p.setStatus(ProfileStatus.INACTIVE);
        p.setLastUpdatedAt(LocalDateTime.now());
        repository.save(p);
    }

    public KongKonnectVerifyResponse verify(KongKonnectVerifyRequest req) {
        return verifyClient.verify(req);
    }

    public KongKonnectVerifyResponse verifySaved(String companyName, String profileName) {
        KongKonnectProfile p = repository.findByCompanyNameAndProfileName(companyName, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + profileName));
        KongKonnectVerifyResponse resp = verifyClient.verify(KongKonnectVerifyRequest.builder()
                .companyName(p.getCompanyName())
                .adminUrl(p.getAdminUrl())
                .konnectPat(p.getKonnectPat())
                .region(p.getRegion())
                .build());
        p.setControlPlanes(resp.getControlPlanes().stream()
                .map(cp -> new KongKonnectControlPlane(cp.getId(), cp.getName()))
                .collect(Collectors.toList()));
        p.setLastUpdatedAt(LocalDateTime.now());
        repository.save(p);
        return resp;
    }

    private Optional<KongKonnectProfile> findActiveById(String id, String companyName) {
        return repository.findById(id)
                .filter(p -> companyName.equals(p.getCompanyName()))
                .filter(this::isActive);
    }

    private boolean isActive(KongKonnectProfile profile) {
        return profile.getStatus() == null || profile.getStatus() == ProfileStatus.ACTIVE;
    }
}
