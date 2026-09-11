package com.forgeshift.profile.config.controller;

import com.forgeshift.profile.config.dto.OnCreate;
import com.forgeshift.profile.config.dto.Wso2ProfileInfoRequest;
import com.forgeshift.profile.config.dto.Wso2ProfileInfoResponse;
import com.forgeshift.profile.config.dto.Wso2ProfileRequest;
import com.forgeshift.profile.config.dto.Wso2ProfileResponse;
import com.forgeshift.profile.config.dto.Wso2VerifyRequest;
import com.forgeshift.profile.config.dto.Wso2VerifyResponse;
import com.forgeshift.profile.config.service.Wso2ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD + verify for WSO2 connection profiles.
 *
 * <pre>
 *   POST    /wso2/profiles/info                                         probe-only: DCR + tenants discovery, no DB write
 *   POST    /wso2/profiles/save                                         persist a profile (requires defaultWso2Tenant)
 *   PUT     /wso2/profiles                                              update (lookup from body; no password
 *                                                                       or defaultWso2Tenant keeps the stored one)
 *   GET     /wso2/profiles?companyName=&profileName=                    read one
 *   GET     /wso2/profiles?companyName=[&wso2Tenant=]                   list (filter by managed tenant)
 *   DELETE  /wso2/profiles?companyName=&profileName=                    delete
 *   POST    /wso2/profiles/verify                                       verify with payload
 *   POST    /wso2/profiles/verify-saved?companyName=&profileName=       verify a saved profile
 * </pre>
 *
 * <p>The create flow is intentionally split in two:
 * <ol>
 *   <li>{@code POST /info} — frontend collects WSO2 baseUrl + admin creds,
 *       service does DCR + tenants discovery, returns the discovered
 *       tenant list. Nothing is written to the database.</li>
 *   <li>User picks a tenant from the dropdown. Frontend calls
 *       {@code POST /save} with the same input + the chosen
 *       {@code defaultWso2Tenant}; the service does DCR again
 *       (idempotent on clientName), persists the row, and the profile
 *       binds to that single tenant.</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/wso2/profiles")
@RequiredArgsConstructor
public class Wso2ProfileController {

    private final Wso2ProfileService service;

    /**
     * Probe-only: discover the tenants on the WSO2 instance using the
     * supplied admin credentials. Nothing is persisted. Lets the UI show
     * a tenant picker before the user commits to a profile.
     */
    @PostMapping("/info")
    public ResponseEntity<Wso2ProfileInfoResponse> info(@Valid @RequestBody Wso2ProfileInfoRequest req) {
        return ResponseEntity.ok(service.info(req));
    }

    /**
     * Persist the profile. {@code defaultWso2Tenant} is required —
     * typically the value the user picked from the {@code /info}
     * response. The profile's {@code tenants[]} is set to
     * {@code [defaultWso2Tenant]} and clientId/clientSecret are
     * generated via DCR (or accepted as-is if the caller supplies
     * existing ones).
     */
    @PostMapping("/save")
    public ResponseEntity<Wso2ProfileResponse> save(@Validated(OnCreate.class) @RequestBody Wso2ProfileRequest req) {
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
