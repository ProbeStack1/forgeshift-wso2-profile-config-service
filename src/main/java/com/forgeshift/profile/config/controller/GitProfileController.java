package com.forgeshift.profile.config.controller;

import com.forgeshift.profile.config.dto.GitProfileRequest;
import com.forgeshift.profile.config.dto.GitProfileResponse;
import com.forgeshift.profile.config.dto.GitVerifyRequest;
import com.forgeshift.profile.config.dto.GitVerifyResponse;
import com.forgeshift.profile.config.dto.OnCreate;
import com.forgeshift.profile.config.service.GitProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GitProfileController {

    private final GitProfileService service;

    @PostMapping("/git/profiles")
    public ResponseEntity<GitProfileResponse> create(@Validated(OnCreate.class) @RequestBody GitProfileRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/git/profiles/{id}")
    public ResponseEntity<GitProfileResponse> update(@PathVariable String id,
                                                     @Valid @RequestBody GitProfileRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @GetMapping("/git/profiles/{id}")
    public ResponseEntity<GitProfileResponse> get(@PathVariable String id,
                                                  @RequestParam String companyName) {
        return ResponseEntity.ok(service.get(id, companyName));
    }

    @GetMapping("/git/profiles")
    public ResponseEntity<List<GitProfileResponse>> getAll(@RequestParam String companyName) {
        return ResponseEntity.ok(service.getAll(companyName));
    }

    @DeleteMapping("/git/profiles/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @RequestParam String companyName,
                                       @RequestParam String userEmail) {
        service.delete(id, companyName, userEmail);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/git/profiles/verify")
    public ResponseEntity<GitVerifyResponse> verify(@Valid @RequestBody GitVerifyRequest request) {
        return ResponseEntity.ok(service.verify(request));
    }

    @PostMapping("/git/profiles/verify-saved")
    public ResponseEntity<GitVerifyResponse> verifySaved(@RequestParam String companyName,
                                                         @RequestParam String profileName) {
        return ResponseEntity.ok(service.verifySaved(companyName, profileName));
    }
}
