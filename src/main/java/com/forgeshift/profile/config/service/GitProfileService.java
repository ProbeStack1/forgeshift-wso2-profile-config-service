package com.forgeshift.profile.config.service;

import com.forgeshift.profile.config.client.GitVerifyClient;
import com.forgeshift.profile.config.domain.GitProfile;
import com.forgeshift.profile.config.domain.ProfileStatus;
import com.forgeshift.profile.config.dto.GitProfileRequest;
import com.forgeshift.profile.config.dto.GitProfileResponse;
import com.forgeshift.profile.config.dto.GitVerifyRequest;
import com.forgeshift.profile.config.dto.GitVerifyResponse;
import com.forgeshift.profile.config.dto.SecretMask;
import com.forgeshift.profile.config.exception.ProfileNotFoundException;
import com.forgeshift.profile.config.repository.GitProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GitProfileService {

    private final GitProfileRepository repository;
    private final GitVerifyClient verifyClient;

    public GitProfileResponse create(GitProfileRequest request) {
        defaultProfileName(request);
        if (!SecretMask.isNewSecret(request.getPat())) {
            throw new IllegalArgumentException("pat is required, and a masked value is not a token");
        }
        boolean exists = repository.existsByProfileNameAndCompanyNameAndStatus(
                request.getProfileName(), request.getCompanyName(), ProfileStatus.ACTIVE);
        if (exists) {
            throw new IllegalStateException("Active Git profile already exists for this company");
        }

        String organization = verifyClient.normalizeOrganization(request.getGithubUrl(), request.getOrganization());
        LocalDateTime now = LocalDateTime.now();
        GitProfile profile = new GitProfile();
        apply(profile, request, organization);
        profile.setPat(request.getPat());
        profile.setStatus(ProfileStatus.ACTIVE);
        profile.setCreatedAt(now);
        profile.setCreatedBy(request.getUserEmail());
        profile.setLastUpdatedAt(now);
        profile.setLastUpdatedBy(request.getUserEmail());
        return GitProfileResponse.from(repository.save(profile));
    }

    public GitProfileResponse update(String id, GitProfileRequest request) {
        defaultProfileName(request);
        GitProfile profile = repository.findByIdAndCompanyNameAndStatus(
                id, request.getCompanyName(), ProfileStatus.ACTIVE)
                .orElseThrow(() -> new ProfileNotFoundException("Git profile not found"));

        apply(profile, request, verifyClient.normalizeOrganization(request.getGithubUrl(), request.getOrganization()));
        // A request without a token - left out, blank, or the mask a read returns - keeps the
        // stored one. No field here decides where it is sent: verify-saved calls api.github.com,
        // and the migration service the GitHub API URL in its own config.
        if (SecretMask.isNewSecret(request.getPat())) {
            profile.setPat(request.getPat());
        }
        profile.setLastUpdatedAt(LocalDateTime.now());
        profile.setLastUpdatedBy(request.getUserEmail());
        return GitProfileResponse.from(repository.save(profile));
    }

    public GitProfileResponse get(String id, String companyName) {
        return repository.findByIdAndCompanyNameAndStatus(id, companyName, ProfileStatus.ACTIVE)
                .map(GitProfileResponse::from)
                .orElseThrow(() -> new ProfileNotFoundException("Git profile not found"));
    }

    public List<GitProfileResponse> getAll(String companyName) {
        return repository.findAllByCompanyNameAndStatus(companyName, ProfileStatus.ACTIVE)
                .stream().map(GitProfileResponse::from).toList();
    }

    public void delete(String id, String companyName, String userEmail) {
        GitProfile profile = repository.findByIdAndCompanyNameAndStatus(id, companyName, ProfileStatus.ACTIVE)
                .orElseThrow(() -> new ProfileNotFoundException("Git profile not found"));
        profile.setStatus(ProfileStatus.INACTIVE);
        profile.setLastUpdatedAt(LocalDateTime.now());
        profile.setLastUpdatedBy(userEmail);
        repository.save(profile);
    }

    public GitVerifyResponse verify(GitVerifyRequest request) {
        return verifyClient.verify(request);
    }

    public GitVerifyResponse verifySaved(String companyName, String profileName) {
        GitProfile profile = repository
                .findByCompanyNameAndProfileNameAndStatus(companyName, profileName, ProfileStatus.ACTIVE)
                .orElseThrow(() -> new ProfileNotFoundException("Git profile not found"));
        GitVerifyResponse response = verifyClient.verify(savedRequest(profile));
        profile.setLastVerifiedAt(LocalDateTime.now());
        profile.setLastVerifiedDetail(String.join("; ", response.getMessages()));
        repository.save(profile);
        return response;
    }

    private void apply(GitProfile profile, GitProfileRequest request, String organization) {
        profile.setProfileName(request.getProfileName());
        profile.setCompanyName(request.getCompanyName());
        profile.setProvider("github");
        profile.setGithubUrl(request.getGithubUrl());
        profile.setOrganization(organization);
        profile.setUsername(cleanOptional(request.getUsername()));
        profile.setTeamName(cleanOptional(request.getTeamName()));
        profile.setRepo(cleanOptional(request.getRepo()));
        profile.setBranch(defaultBranch(request.getBranch()));
    }

    private GitVerifyRequest savedRequest(GitProfile profile) {
        GitVerifyRequest request = new GitVerifyRequest();
        request.setCompanyName(profile.getCompanyName());
        request.setGithubUrl(profile.getGithubUrl());
        request.setOrganization(profile.getOrganization());
        request.setUsername(profile.getUsername());
        request.setTeamName(profile.getTeamName());
        request.setPat(profile.getPat());
        return request;
    }

    private void defaultProfileName(GitProfileRequest request) {
        if (request.getProfileName() == null || request.getProfileName().isBlank()) {
            request.setProfileName("primary");
        }
    }

    /**
     * The migration service reads the branch straight off this profile with no fallback of
     * its own, so a profile saved without one must still be usable.
     */
    private static String defaultBranch(String value) {
        return StringUtils.hasText(value) ? value.trim() : "main";
    }

    private static String cleanOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
