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

    @NotBlank
    private String githubUrl;

    @NotBlank
    private String organization;

    private String username;

    private String teamName;

    @NotBlank
    private String pat;
}
