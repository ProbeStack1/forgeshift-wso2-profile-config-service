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
    private String repository;
    private String repo;
    private String branch;
    private String configPath;
    private String username;
    private String teamName;
    private boolean valid;
    private boolean repoExists;
    private boolean branchExists;
    private boolean writeAccess;
    private boolean workflowWritable;
    @Builder.Default
    private List<String> messages = new ArrayList<>();
}
