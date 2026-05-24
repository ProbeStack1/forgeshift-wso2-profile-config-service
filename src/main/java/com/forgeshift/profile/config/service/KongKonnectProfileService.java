package com.forgeshift.profile.config.service;

import com.forgeshift.profile.config.client.KongKonnectVerifyClient;
import com.forgeshift.profile.config.domain.KongKonnectProfile;
import com.forgeshift.profile.config.dto.KongKonnectProfileRequest;
import com.forgeshift.profile.config.dto.KongKonnectProfileResponse;
import com.forgeshift.profile.config.dto.KongKonnectVerifyRequest;
import com.forgeshift.profile.config.dto.KongKonnectVerifyResponse;
import com.forgeshift.profile.config.exception.ProfileNotFoundException;
import com.forgeshift.profile.config.repository.KongKonnectProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KongKonnectProfileService {

    private final KongKonnectProfileRepository repository;
    private final KongKonnectVerifyClient verifyClient;

    public KongKonnectProfileResponse create(KongKonnectProfileRequest req) {
        repository.findByCompanyNameAndProfileName(req.getCompanyName(), req.getProfileName())
                .ifPresent(p -> { throw new IllegalStateException("Profile already exists: " + p.getId()); });
        KongKonnectProfile p = KongKonnectProfile.builder()
                .id(compositeId(req))
                .companyName(req.getCompanyName())
                .profileName(req.getProfileName())
                .konnectBaseUrl(req.getKonnectBaseUrl())
                .konnectAccessToken(req.getKonnectAccessToken())
                .controlPlaneId(req.getControlPlaneId())
                .region(req.getRegion())
                .notes(req.getNotes())
                .createdBy(req.getUserEmail())
                .lastModifiedBy(req.getUserEmail())
                .build();
        return KongKonnectProfileResponse.from(repository.save(p));
    }

    public KongKonnectProfileResponse update(String id, KongKonnectProfileRequest req) {
        KongKonnectProfile p = repository.findById(id)
                .orElseThrow(() -> new ProfileNotFoundException("Profile not found: " + id));
        p.setKonnectBaseUrl(req.getKonnectBaseUrl());
        p.setKonnectAccessToken(req.getKonnectAccessToken());
        p.setControlPlaneId(req.getControlPlaneId());
        p.setRegion(req.getRegion());
        p.setNotes(req.getNotes());
        p.setLastModifiedBy(req.getUserEmail());
        return KongKonnectProfileResponse.from(repository.save(p));
    }

    public KongKonnectProfileResponse get(String companyName, String profileName) {
        return KongKonnectProfileResponse.from(repository
                .findByCompanyNameAndProfileName(companyName, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + profileName)));
    }

    public List<KongKonnectProfileResponse> list(String companyName) {
        return repository.findByCompanyName(companyName).stream()
                .map(KongKonnectProfileResponse::from)
                .collect(Collectors.toList());
    }

    public void delete(String companyName, String profileName) {
        KongKonnectProfile p = repository.findByCompanyNameAndProfileName(companyName, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + profileName));
        repository.deleteById(p.getId());
    }

    public KongKonnectVerifyResponse verify(KongKonnectVerifyRequest req) {
        return verifyClient.verify(req);
    }

    public KongKonnectVerifyResponse verifySaved(String companyName, String profileName) {
        KongKonnectProfile p = repository.findByCompanyNameAndProfileName(companyName, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + profileName));
        KongKonnectVerifyResponse resp = verifyClient.verify(KongKonnectVerifyRequest.builder()
                .konnectBaseUrl(p.getKonnectBaseUrl())
                .konnectAccessToken(p.getKonnectAccessToken())
                .controlPlaneId(p.getControlPlaneId())
                .build());
        if (resp.isSuccess()) {
            p.setLastVerifiedAt(Instant.now());
            p.setLastVerifiedControlPlaneName(resp.getControlPlaneName());
            repository.save(p);
        }
        return resp;
    }

    private static String compositeId(KongKonnectProfileRequest req) {
        return req.getCompanyName() + "|" + req.getProfileName();
    }
}
