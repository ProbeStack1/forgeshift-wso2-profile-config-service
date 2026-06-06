package com.forgeshift.profile.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitVerifyResponse {

    private String companyName;
    private String provider;
    private String githubUrl;
    private String organization;
    private String username;
    private String teamName;
    private boolean valid;
    private boolean tokenValid;
    private boolean organizationAccessible;
    @Builder.Default
    private List<String> messages = new ArrayList<>();
}
