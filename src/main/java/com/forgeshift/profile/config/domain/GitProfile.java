package com.forgeshift.profile.config.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("git_profiles")
@CompoundIndexes({
        @CompoundIndex(name = "unique_active_git_profile_per_company",
                def = "{'profileName': 1, 'companyName': 1, 'status': 1}", unique = true)
})
public class GitProfile {

    @Id
    private String id;

    private String profileName;
    private String companyName;
    private String provider;
    private String githubUrl;
    private String organization;
    private String repository;
    private String repo;
    private String branch;
    private String configPath;
    private String username;
    private String teamName;
    private String pat;
    private ProfileStatus status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime lastUpdatedAt;
    private String lastUpdatedBy;
    private LocalDateTime lastVerifiedAt;
    private String lastVerifiedDetail;
}
