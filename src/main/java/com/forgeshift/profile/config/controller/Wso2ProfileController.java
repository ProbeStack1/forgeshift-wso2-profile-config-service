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
 *   POST    /wso2/profiles                                                create
 *   PUT     /wso2/profiles                                                update (lookup from body)
 *   GET     /wso2/profiles?companyName=&wso2Tenant=&profileName=          read one
 *   GET     /wso2/profiles?companyName=[&wso2Tenant=]                     list
 *   DELETE  /wso2/profiles?companyName=&wso2Tenant=&profileName=          delete
 *   POST    /wso2/profiles/verify                                         verify with payload
 *   POST    /wso2/profiles/verify-saved?companyName=&wso2Tenant=&profileName=
 *
 * On create, the service auto-discovers tenant domains from WSO2 using the
 * supplied admin credentials — the caller doesn't have to know wso2Tenant
 * up front. The discovered list is persisted on the profile and echoed back
 * in the response.
 *
 * Note: PUT intentionally has no {id} path-var. The composite id contains
 * pipe characters which need URL-encoding and trip Tomcat's default
 * strict-path validation. We derive the id from the request body instead.
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
        String id = req.getCompanyName() + "|" + req.getWso2Tenant() + "|" + req.getProfileName();
        return ResponseEntity.ok(service.update(id, req));
    }

    @GetMapping(params = "profileName")
    public ResponseEntity<Wso2ProfileResponse> getOne(@RequestParam String companyName,
                                                     @RequestParam String wso2Tenant,
                                                     @RequestParam String profileName) {
        return ResponseEntity.ok(service.get(companyName, wso2Tenant, profileName));
    }

    @GetMapping
    public ResponseEntity<List<Wso2ProfileResponse>> list(@RequestParam String companyName,
                                                          @RequestParam(required = false) String wso2Tenant) {
        return ResponseEntity.ok(service.list(companyName, wso2Tenant));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam String companyName,
                                       @RequestParam String wso2Tenant,
                                       @RequestParam String profileName) {
        service.delete(companyName, wso2Tenant, profileName);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<Wso2VerifyResponse> verify(@Valid @RequestBody Wso2VerifyRequest req) {
        return ResponseEntity.ok(service.verify(req));
    }

    @PostMapping("/verify-saved")
    public ResponseEntity<Wso2VerifyResponse> verifySaved(@RequestParam String companyName,
                                                          @RequestParam String wso2Tenant,
                                                          @RequestParam String profileName) {
        return ResponseEntity.ok(service.verifySaved(companyName, wso2Tenant, profileName));
    }
}
