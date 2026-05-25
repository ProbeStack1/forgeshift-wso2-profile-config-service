package com.forgeshift.profile.config.controller;

import com.forgeshift.profile.config.dto.Wso2ProfileRequest;
import com.forgeshift.profile.config.dto.Wso2ProfileResponse;
import com.forgeshift.profile.config.dto.Wso2VerifyRequest;
import com.forgeshift.profile.config.dto.Wso2VerifyResponse;
import com.forgeshift.profile.config.service.Wso2ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD + verify for WSO2 connection profiles.
 *
 *   POST    /wso2/profiles                                              create
 *   PUT     /wso2/profiles                                              update (lookup from body)
 *   GET     /wso2/profiles?companyName=&profileName=                    read one
 *   GET     /wso2/profiles?companyName=[&wso2Tenant=]                   list (filter by managed tenant)
 *   DELETE  /wso2/profiles?companyName=&profileName=                    delete
 *   POST    /wso2/profiles/verify                                       verify with payload
 *   POST    /wso2/profiles/verify-saved?companyName=&profileName=       verify a saved profile
 *
 * <p>On create, the service auto-discovers tenant domains from WSO2 using
 * the supplied admin credentials and stores them on the single profile
 * document (alongside {@code carbon.super}). The discovery service looks
 * profiles up by scanning that tenants array.
 */
@Slf4j
@RestController
@RequestMapping("/wso2/profiles")
@RequiredArgsConstructor
public class Wso2ProfileController {

    private final Wso2ProfileService service;

    @PostMapping
    public ResponseEntity<Wso2ProfileResponse> create(@Valid @RequestBody Wso2ProfileRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PutMapping
    public ResponseEntity<Wso2ProfileResponse> update(@Valid @RequestBody Wso2ProfileRequest req) {
        return ResponseEntity.ok(service.update(req.getCompanyName(), req.getProfileName(), req));
    }

    @GetMapping(params = "profileName")
    public ResponseEntity<Wso2ProfileResponse> getOne(@RequestParam String companyName,
                                                     @RequestParam String profileName) {
        return ResponseEntity.ok(service.get(companyName, profileName));
    }

    @GetMapping
    public ResponseEntity<List<Wso2ProfileResponse>> list(@RequestParam String companyName,
                                                          @RequestParam(required = false) String wso2Tenant) {
        return ResponseEntity.ok(service.list(companyName, wso2Tenant));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam String companyName,
                                       @RequestParam String profileName) {
        service.delete(companyName, profileName);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<Wso2VerifyResponse> verify(@Valid @RequestBody Wso2VerifyRequest req) {
        return ResponseEntity.ok(service.verify(req));
    }

    @PostMapping("/verify-saved")
    public ResponseEntity<Wso2VerifyResponse> verifySaved(@RequestParam String companyName,
                                                          @RequestParam String profileName) {
        return ResponseEntity.ok(service.verifySaved(companyName, profileName));
    }
}
