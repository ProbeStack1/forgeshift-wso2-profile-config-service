package com.forgeshift.profile.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GitProfileRequest {

    private String profileName;

    @NotBlank
    private String companyName;

    @NotBlank
    private String githubUrl;

    @NotBlank
    private String organization;

    private String username;

    private String teamName;

    /** {@code owner/repo} the migration commits the generated Kong config to. */
    private String repo;

    /** Branch within {@link #repo}; blank defaults to {@code main}. */
    private String branch;

    @NotBlank
    private String pat;

    private String userEmail;
}
