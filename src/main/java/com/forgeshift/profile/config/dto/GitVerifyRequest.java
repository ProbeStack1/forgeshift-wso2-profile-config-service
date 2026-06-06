package com.forgeshift.profile.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GitVerifyRequest {

    @NotBlank
    private String companyName;

    private String provider;

    @NotBlank
    private String githubUrl;

    private String organization;
    private String repository;
    private String repo;

    @NotBlank
    private String branch;

    private String configPath;

    @NotBlank
    private String username;

    private String teamName;

    @NotBlank
    private String pat;
}
