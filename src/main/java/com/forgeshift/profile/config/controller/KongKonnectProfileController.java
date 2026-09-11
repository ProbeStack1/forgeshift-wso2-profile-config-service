package com.forgeshift.profile.config.controller;

import com.forgeshift.profile.config.dto.KongKonnectProfileRequest;
import com.forgeshift.profile.config.dto.KongKonnectProfileResponse;
import com.forgeshift.profile.config.dto.KongKonnectVerifyRequest;
import com.forgeshift.profile.config.dto.KongKonnectVerifyResponse;
import com.forgeshift.profile.config.dto.OnCreate;
import com.forgeshift.profile.config.service.KongKonnectProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class KongKonnectProfileController {

    private final KongKonnectProfileService service;

    @PostMapping("/kong-konnect/profiles")
    public ResponseEntity<KongKonnectProfileResponse> create(
            @Validated(OnCreate.class) @RequestBody KongKonnectProfileRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PutMapping("/kong-konnect/profiles/{id}")
    public ResponseEntity<KongKonnectProfileResponse> update(@PathVariable String id,
                                                             @Valid @RequestBody KongKonnectProfileRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @GetMapping("/kong-konnect/profiles/{id}")
    public ResponseEntity<KongKonnectProfileResponse> get(@PathVariable String id,
                                                          @RequestParam String companyName) {
        return ResponseEntity.ok(service.get(id, companyName));
    }

    @GetMapping("/kong-konnect/profiles")
    public ResponseEntity<List<KongKonnectProfileResponse>> getAll(@RequestParam String companyName) {
        return ResponseEntity.ok(service.getAll(companyName));
    }

    @PutMapping("/kong-konnect/profiles/{id}/default")
    public ResponseEntity<KongKonnectProfileResponse> setDefault(@PathVariable String id,
                                                                 @RequestParam String companyName,
                                                                 @RequestParam String userEmail) {
        return ResponseEntity.ok(service.setDefault(id, companyName, userEmail));
    }

    @DeleteMapping("/kong-konnect/profiles/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @RequestParam String companyName,
                                       @RequestParam String userEmail) {
        service.delete(id, companyName, userEmail);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/kong-konnect/profiles/verify")
    public ResponseEntity<KongKonnectVerifyResponse> verify(@Valid @RequestBody KongKonnectVerifyRequest req) {
        return ResponseEntity.ok(service.verifyConnection(req));
    }
}
