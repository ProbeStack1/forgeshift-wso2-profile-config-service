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

    @NotBlank
    private String username;

    private String teamName;

    @NotBlank
    private String pat;

    private String userEmail;
}
