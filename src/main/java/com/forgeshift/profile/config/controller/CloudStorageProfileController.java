package com.forgeshift.profile.config.controller;

import com.forgeshift.profile.config.dto.CloudStorageProfileRequest;
import com.forgeshift.profile.config.dto.CloudStorageProfileResponse;
import com.forgeshift.profile.config.dto.CloudStorageVerifyResponse;
import com.forgeshift.profile.config.service.CloudStorageProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * CRUD + verify for GCS connection profiles.
 *
 *   POST    /cloud-storage/profiles                                 create (JSON, base64 SA)
 *   POST    /cloud-storage/profiles/upload                          create (multipart, file upload)
 *   PUT     /cloud-storage/profiles/{id}                            update
 *   GET     /cloud-storage/profiles?companyName=&profileName=       read one
 *   GET     /cloud-storage/profiles?companyName=                    list
 *   DELETE  /cloud-storage/profiles?companyName=&profileName=       delete
 *   POST    /cloud-storage/profiles/verify-upload                   verify with multipart upload
 *   POST    /cloud-storage/profiles/verify-saved?...                verify a saved profile
 */
@Slf4j
@RestController
@RequestMapping("/cloud-storage/profiles")
@RequiredArgsConstructor
public class CloudStorageProfileController {

    private final CloudStorageProfileService service;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CloudStorageProfileResponse> create(@Valid @RequestBody CloudStorageProfileRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CloudStorageProfileResponse> upload(
            @RequestParam String companyName,
            @RequestParam String profileName,
            @RequestParam String bucket,
            @RequestParam(required = false) String objectPrefix,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String userEmail,
            @RequestParam("serviceAccount") MultipartFile serviceAccount) throws IOException {
        return ResponseEntity.ok(service.createFromBytes(
                companyName, profileName, bucket, objectPrefix, notes, userEmail,
                serviceAccount.getBytes()));
    }

    /** Update keyed by (companyName, profileName) in the body. See class javadoc. */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CloudStorageProfileResponse> update(@Valid @RequestBody CloudStorageProfileRequest req) {
        String id = req.getCompanyName() + "|" + req.getProfileName();
        return ResponseEntity.ok(service.update(id, req));
    }

    @GetMapping(params = "profileName")
    public ResponseEntity<CloudStorageProfileResponse> getOne(@RequestParam String companyName,
                                                              @RequestParam String profileName) {
        return ResponseEntity.ok(service.get(companyName, profileName));
    }

    @GetMapping
    public ResponseEntity<List<CloudStorageProfileResponse>> list(@RequestParam String companyName) {
        return ResponseEntity.ok(service.list(companyName));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam String companyName,
                                       @RequestParam String profileName) {
        service.delete(companyName, profileName);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/verify-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CloudStorageVerifyResponse> verifyUpload(
            @RequestParam String bucket,
            @RequestParam("serviceAccount") MultipartFile serviceAccount) throws IOException {
        return ResponseEntity.ok(service.verifyFromBytes(bucket, serviceAccount.getBytes()));
    }

    @PostMapping("/verify-saved")
    public ResponseEntity<CloudStorageVerifyResponse> verifySaved(@RequestParam String companyName,
                                                                  @RequestParam String profileName) {
        return ResponseEntity.ok(service.verifySaved(companyName, profileName));
    }
}
