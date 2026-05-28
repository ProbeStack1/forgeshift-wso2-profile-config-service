package com.forgeshift.profile.config.controller;

import com.forgeshift.profile.config.dto.KongKonnectProfileRequest;
import com.forgeshift.profile.config.dto.KongKonnectProfileResponse;
import com.forgeshift.profile.config.dto.KongKonnectVerifyRequest;
import com.forgeshift.profile.config.dto.KongKonnectVerifyResponse;
import com.forgeshift.profile.config.service.KongKonnectProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/kong-konnect/profiles")
@RequiredArgsConstructor
public class KongKonnectProfileController {

    private final KongKonnectProfileService service;

    @PostMapping
    public ResponseEntity<KongKonnectProfileResponse> create(@Valid @RequestBody KongKonnectProfileRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<KongKonnectProfileResponse> updateById(@PathVariable String id,
                                                                 @Valid @RequestBody KongKonnectProfileRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @PutMapping
    public ResponseEntity<KongKonnectProfileResponse> update(@Valid @RequestBody KongKonnectProfileRequest req) {
        String id = req.getCompanyName() + "|" + req.getProfileName();
        return ResponseEntity.ok(service.update(id, req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KongKonnectProfileResponse> getById(@PathVariable String id,
                                                              @RequestParam String companyName) {
        return ResponseEntity.ok(service.getById(id, companyName));
    }

    @GetMapping(params = "profileName")
    public ResponseEntity<KongKonnectProfileResponse> getOne(@RequestParam String companyName,
                                                            @RequestParam String profileName) {
        return ResponseEntity.ok(service.getByProfileName(companyName, profileName));
    }

    @GetMapping
    public ResponseEntity<List<KongKonnectProfileResponse>> list(@RequestParam String companyName) {
        return ResponseEntity.ok(service.list(companyName));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam String companyName,
                                       @RequestParam String profileName) {
        service.delete(companyName, profileName);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable String id,
                                           @RequestParam String companyName,
                                           @RequestParam String userEmail) {
        service.delete(id, companyName, userEmail);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<KongKonnectVerifyResponse> verify(@Valid @RequestBody KongKonnectVerifyRequest req) {
        return ResponseEntity.ok(service.verify(req));
    }

    @PostMapping("/verify-saved")
    public ResponseEntity<KongKonnectVerifyResponse> verifySaved(@RequestParam String companyName,
                                                                 @RequestParam String profileName) {
        return ResponseEntity.ok(service.verifySaved(companyName, profileName));
    }
}
