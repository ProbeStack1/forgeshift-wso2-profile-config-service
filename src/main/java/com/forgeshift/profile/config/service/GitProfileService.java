package com.forgeshift.profile.config.service;

import com.forgeshift.profile.config.client.GitVerifyClient;
import com.forgeshift.profile.config.domain.GitProfile;
import com.forgeshift.profile.config.domain.ProfileStatus;
import com.forgeshift.profile.config.dto.GitProfileRequest;
import com.forgeshift.profile.config.dto.GitVerifyRequest;
import com.forgeshift.profile.config.dto.GitVerifyResponse;
import com.forgeshift.profile.config.exception.ProfileNotFoundException;
import com.forgeshift.profile.config.repository.GitProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GitProfileService {

    private final GitProfileRepository repository;
    private final GitVerifyClient verifyClient;

    public GitProfile create(GitProfileRequest request) {
        boolean exists = repository.existsByProfileNameAndCompanyNameAndStatus(
                request.getProfileName(), request.getCompanyName(), ProfileStatus.ACTIVE);
        if (exists) {
            throw new IllegalStateException("Active Git profile already exists for this company");
        }

        GitVerifyClient.NormalizedGit git = normalize(request);
        LocalDateTime now = LocalDateTime.now();
        GitProfile profile = new GitProfile();
        apply(profile, request, git);
        profile.setStatus(ProfileStatus.ACTIVE);
        profile.setCreatedAt(now);
        profile.setCreatedBy(request.getUserEmail());
        profile.setLastUpdatedAt(now);
        profile.setLastUpdatedBy(request.getUserEmail());
        return repository.save(profile);
    }

    public GitProfile update(String id, GitProfileRequest request) {
        GitProfile profile = repository.findByIdAndCompanyNameAndStatus(
                id, request.getCompanyName(), ProfileStatus.ACTIVE)
                .orElseThrow(() -> new ProfileNotFoundException("Git profile not found"));

        apply(profile, request, normalize(request));
        profile.setLastUpdatedAt(LocalDateTime.now());
        profile.setLastUpdatedBy(request.getUserEmail());
        return repository.save(profile);
    }

    public GitProfile get(String id, String companyName) {
        return repository.findByIdAndCompanyNameAndStatus(id, companyName, ProfileStatus.ACTIVE)
                .orElseThrow(() -> new ProfileNotFoundException("Git profile not found"));
    }

    public List<GitProfile> getAll(String companyName) {
        return repository.findAllByCompanyNameAndStatus(companyName, ProfileStatus.ACTIVE);
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

    private GitVerifyClient.NormalizedGit normalize(GitProfileRequest request) {
        return verifyClient.normalize(request.getGithubUrl(), request.getOrganization(),
                request.getRepository(), request.getRepo(), request.getBranch(), request.getConfigPath());
    }

    private void apply(GitProfile profile, GitProfileRequest request, GitVerifyClient.NormalizedGit git) {
        profile.setProfileName(request.getProfileName());
        profile.setCompanyName(request.getCompanyName());
        profile.setProvider("github");
        profile.setGithubUrl(request.getGithubUrl());
        profile.setOrganization(git.organization());
        profile.setRepository(git.repository());
        profile.setRepo(git.repo());
        profile.setBranch(git.branch());
        profile.setConfigPath(git.configPath());
        profile.setUsername(request.getUsername());
        profile.setTeamName(request.getTeamName());
        profile.setPat(request.getPat());
    }

    private GitVerifyRequest savedRequest(GitProfile profile) {
        GitVerifyRequest request = new GitVerifyRequest();
        request.setCompanyName(profile.getCompanyName());
        request.setProvider(profile.getProvider());
        request.setGithubUrl(profile.getGithubUrl());
        request.setOrganization(profile.getOrganization());
        request.setRepository(profile.getRepository());
        request.setRepo(profile.getRepo());
        request.setBranch(profile.getBranch());
        request.setConfigPath(profile.getConfigPath());
        request.setUsername(profile.getUsername());
        request.setTeamName(profile.getTeamName());
        request.setPat(profile.getPat());
        return request;
    }
}
