package com.forgeshift.profile.config.client;

import com.forgeshift.profile.config.dto.GitVerifyRequest;
import com.forgeshift.profile.config.dto.GitVerifyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GitVerifyClient {

    private final WebClient.Builder webClientBuilder;

    public GitVerifyResponse verify(GitVerifyRequest request) {
        NormalizedGit normalized = normalize(
                request.getGithubUrl(),
                request.getOrganization(),
                request.getRepository(),
                request.getRepo(),
                request.getBranch(),
                request.getConfigPath());

        List<String> messages = new ArrayList<>();
        boolean repoExists = false;
        boolean branchExists = false;
        boolean writeAccess = false;
        boolean workflowWritable = false;

        WebClient github = webClientBuilder
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + request.getPat())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();

        try {
            Map<?, ?> repoBody = github.get()
                    .uri("/repos/{owner}/{repo}", normalized.organization(), normalized.repository())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            repoExists = repoBody != null;
            Object permissions = repoBody != null ? repoBody.get("permissions") : null;
            if (permissions instanceof Map<?, ?> permissionMap) {
                writeAccess = Boolean.TRUE.equals(permissionMap.get("push"))
                        || Boolean.TRUE.equals(permissionMap.get("admin"));
            }
            if (repoExists) {
                messages.add("Repository exists: " + normalized.repo());
            }
            if (writeAccess) {
                messages.add("Token has repository write access.");
            } else {
                messages.add("Token does not report push/admin access for this repository.");
            }
        } catch (WebClientResponseException e) {
            messages.add("Repository check failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        }

        try {
            github.get()
                    .uri("/repos/{owner}/{repo}/branches/{branch}",
                            normalized.organization(), normalized.repository(), normalized.branch())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            branchExists = true;
            messages.add("Branch exists: " + normalized.branch());
        } catch (WebClientResponseException e) {
            messages.add("Branch check failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        }

        if (writeAccess) {
            workflowWritable = true;
            messages.add("Workflow path can be written if the token includes workflow scope.");
        }

        return GitVerifyResponse.builder()
                .companyName(request.getCompanyName())
                .provider("github")
                .githubUrl(request.getGithubUrl())
                .organization(normalized.organization())
                .repository(normalized.repository())
                .repo(normalized.repo())
                .branch(normalized.branch())
                .configPath(normalized.configPath())
                .username(request.getUsername())
                .teamName(request.getTeamName())
                .repoExists(repoExists)
                .branchExists(branchExists)
                .writeAccess(writeAccess)
                .workflowWritable(workflowWritable)
                .valid(repoExists && branchExists && writeAccess)
                .messages(messages)
                .build();
    }

    public NormalizedGit normalize(String githubUrl, String organization, String repository,
                                   String repo, String branch, String configPath) {
        String normalizedRepo = clean(repo);
        String normalizedOrg = clean(organization);
        String normalizedRepository = clean(repository);

        if (!StringUtils.hasText(normalizedRepo) && StringUtils.hasText(githubUrl)) {
            String[] parts = githubUrl.replace("https://github.com/", "")
                    .replace("http://github.com/", "")
                    .replace("git@github.com:", "")
                    .replace(".git", "")
                    .split("/");
            if (parts.length >= 1 && !StringUtils.hasText(normalizedOrg)) {
                normalizedOrg = clean(parts[0]);
            }
            if (parts.length >= 2 && !StringUtils.hasText(normalizedRepository)) {
                normalizedRepository = clean(parts[1]);
            }
        }

        if (!StringUtils.hasText(normalizedRepo)
                && StringUtils.hasText(normalizedOrg)
                && StringUtils.hasText(normalizedRepository)) {
            normalizedRepo = normalizedOrg + "/" + normalizedRepository;
        }

        if (!StringUtils.hasText(normalizedRepo) || normalizedRepo.split("/").length != 2) {
            throw new IllegalArgumentException("Git repo must be provided as owner/repo or a GitHub repository URL.");
        }

        String[] repoParts = normalizedRepo.split("/");
        normalizedOrg = repoParts[0];
        normalizedRepository = repoParts[1];
        if (!StringUtils.hasText(branch)) {
            throw new IllegalArgumentException("branch is required");
        }

        return new NormalizedGit(
                normalizedOrg,
                normalizedRepository,
                normalizedRepo,
                branch.trim(),
                cleanPath(configPath));
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String cleanPath(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String path = value.trim().replace("\\", "/");
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.toLowerCase(Locale.ROOT).equals(".") ? "" : path;
    }

    public record NormalizedGit(String organization, String repository, String repo,
                                String branch, String configPath) {
    }
}
