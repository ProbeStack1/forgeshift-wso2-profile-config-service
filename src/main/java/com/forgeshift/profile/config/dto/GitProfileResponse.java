package com.forgeshift.profile.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forgeshift.profile.config.domain.GitProfile;
import com.forgeshift.profile.config.domain.ProfileStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Public representation of a GitProfile: every field of the document except the personal
 * access token, which exists only server-side and in the migration service that reads it from
 * Mongo to push the generated Kong config.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GitProfileResponse {

    private String id;
    private String profileName;
    private String companyName;
    private String provider;
    private String githubUrl;
    private String organization;
    private String username;
    private String teamName;
    private String repo;
    private String branch;
    /**
     * {@code "***"} when a token is stored. An update that leaves pat out, or sends this back,
     * keeps the stored token.
     */
    private String patStored;
    private ProfileStatus status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime lastUpdatedAt;
    private String lastUpdatedBy;
    private LocalDateTime lastVerifiedAt;
    private String lastVerifiedDetail;

    public static GitProfileResponse from(GitProfile p) {
        return GitProfileResponse.builder()
                .id(p.getId())
                .profileName(p.getProfileName())
                .companyName(p.getCompanyName())
                .provider(p.getProvider())
                .githubUrl(p.getGithubUrl())
                .organization(p.getOrganization())
                .username(p.getUsername())
                .teamName(p.getTeamName())
                .repo(p.getRepo())
                .branch(p.getBranch())
                .patStored(SecretMask.of(p.getPat()))
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .createdBy(p.getCreatedBy())
                .lastUpdatedAt(p.getLastUpdatedAt())
                .lastUpdatedBy(p.getLastUpdatedBy())
                .lastVerifiedAt(p.getLastVerifiedAt())
                .lastVerifiedDetail(p.getLastVerifiedDetail())
                .build();
    }
}
