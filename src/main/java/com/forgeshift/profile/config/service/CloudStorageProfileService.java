package com.forgeshift.profile.config.service;

import com.forgeshift.profile.config.client.CloudStorageVerifyClient;
import com.forgeshift.profile.config.domain.CloudStorageProfile;
import com.forgeshift.profile.config.dto.CloudStorageProfileRequest;
import com.forgeshift.profile.config.dto.CloudStorageProfileResponse;
import com.forgeshift.profile.config.dto.CloudStorageVerifyResponse;
import com.forgeshift.profile.config.exception.ProfileNotFoundException;
import com.forgeshift.profile.config.repository.CloudStorageProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudStorageProfileService {

    private final CloudStorageProfileRepository repository;
    private final ServiceAccountFileProcessor processor;
    private final CloudStorageVerifyClient verifyClient;

    public CloudStorageProfileResponse create(CloudStorageProfileRequest req) {
        repository.findByCompanyNameAndProfileName(req.getCompanyName(), req.getProfileName())
                .ifPresent(p -> { throw new IllegalStateException("Profile already exists: " + p.getId()); });

        ServiceAccountFileProcessor.ServiceAccountInfo info = processor.parseBase64(req.getServiceAccountJsonBase64());
        CloudStorageProfile p = CloudStorageProfile.builder()
                .id(compositeId(req))
                .companyName(req.getCompanyName())
                .profileName(req.getProfileName())
                .bucket(req.getBucket())
                .projectId(info.getProjectId())
                .clientEmail(info.getClientEmail())
                .privateKeyId(info.getPrivateKeyId())
                .serviceAccountJsonBase64(req.getServiceAccountJsonBase64())
                .objectPrefix(req.getObjectPrefix())
                .notes(req.getNotes())
                .createdBy(req.getUserEmail())
                .lastModifiedBy(req.getUserEmail())
                .build();
        return CloudStorageProfileResponse.from(repository.save(p));
    }

    /** Variant used by the multipart upload endpoint. */
    public CloudStorageProfileResponse createFromBytes(String companyName, String profileName,
                                                       String bucket, String objectPrefix,
                                                       String notes, String userEmail,
                                                       byte[] saJsonBytes) {
        return create(CloudStorageProfileRequest.builder()
                .companyName(companyName)
                .profileName(profileName)
                .bucket(bucket)
                .objectPrefix(objectPrefix)
                .notes(notes)
                .userEmail(userEmail)
                .serviceAccountJsonBase64(processor.toBase64(saJsonBytes))
                .build());
    }

    public CloudStorageProfileResponse update(String id, CloudStorageProfileRequest req) {
        CloudStorageProfile p = repository.findById(id)
                .orElseThrow(() -> new ProfileNotFoundException("Profile not found: " + id));
        ServiceAccountFileProcessor.ServiceAccountInfo info = processor.parseBase64(req.getServiceAccountJsonBase64());
        p.setBucket(req.getBucket());
        p.setProjectId(info.getProjectId());
        p.setClientEmail(info.getClientEmail());
        p.setPrivateKeyId(info.getPrivateKeyId());
        p.setServiceAccountJsonBase64(req.getServiceAccountJsonBase64());
        p.setObjectPrefix(req.getObjectPrefix());
        p.setNotes(req.getNotes());
        p.setLastModifiedBy(req.getUserEmail());
        return CloudStorageProfileResponse.from(repository.save(p));
    }

    public CloudStorageProfileResponse get(String companyName, String profileName) {
        return CloudStorageProfileResponse.from(repository
                .findByCompanyNameAndProfileName(companyName, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + profileName)));
    }

    public List<CloudStorageProfileResponse> list(String companyName) {
        return repository.findByCompanyName(companyName).stream()
                .map(CloudStorageProfileResponse::from)
                .collect(Collectors.toList());
    }

    public void delete(String companyName, String profileName) {
        CloudStorageProfile p = repository.findByCompanyNameAndProfileName(companyName, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + profileName));
        repository.deleteById(p.getId());
    }

    /** Verify with a fresh payload (base64 or raw upload). */
    public CloudStorageVerifyResponse verifyFromBytes(String bucket, byte[] saJsonBytes) {
        return verifyClient.verify(saJsonBytes, bucket);
    }

    public CloudStorageVerifyResponse verifySaved(String companyName, String profileName) {
        CloudStorageProfile p = repository.findByCompanyNameAndProfileName(companyName, profileName)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found: " + companyName + "|" + profileName));
        CloudStorageVerifyResponse resp = verifyClient.verify(
                processor.fromBase64(p.getServiceAccountJsonBase64()), p.getBucket());
        if (resp.isSuccess()) {
            p.setLastVerifiedAt(Instant.now());
            p.setLastVerifiedDetail("sampled=" + resp.getSampleCount());
            repository.save(p);
        }
        return resp;
    }

    private static String compositeId(CloudStorageProfileRequest req) {
        return req.getCompanyName() + "|" + req.getProfileName();
    }
}
