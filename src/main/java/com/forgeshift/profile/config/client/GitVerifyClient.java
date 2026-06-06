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
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GitVerifyClient {

    private final WebClient.Builder webClientBuilder;

    public GitVerifyResponse verify(GitVerifyRequest request) {
        String organization = normalizeOrganization(request.getGithubUrl(), request.getOrganization());

        List<String> messages = new ArrayList<>();
        boolean tokenValid = false;
        boolean organizationAccessible = false;

        WebClient github = webClientBuilder
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + request.getPat())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();

        try {
            Map<?, ?> userBody = github.get()
                    .uri("/user")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            tokenValid = userBody != null;
            if (tokenValid) {
                messages.add("GitHub token is valid.");
            }
        } catch (WebClientResponseException e) {
            messages.add("Token check failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        }

        try {
            github.get()
                    .uri("/orgs/{org}", organization)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            organizationAccessible = true;
            messages.add("Organization is accessible: " + organization);
        } catch (WebClientResponseException e) {
            messages.add("Organization check failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        }

        return GitVerifyResponse.builder()
                .companyName(request.getCompanyName())
                .provider("github")
                .githubUrl(request.getGithubUrl())
                .organization(organization)
                .username(request.getUsername())
                .teamName(request.getTeamName())
                .tokenValid(tokenValid)
                .organizationAccessible(organizationAccessible)
                .valid(tokenValid && organizationAccessible)
                .messages(messages)
                .build();
    }

    public String normalizeOrganization(String githubUrl, String organization) {
        String normalizedOrg = clean(organization);
        if (StringUtils.hasText(normalizedOrg)) {
            return normalizedOrg;
        }
        if (StringUtils.hasText(githubUrl)) {
            String path = githubUrl.replace("https://github.com/", "")
                    .replace("http://github.com/", "");
            String[] parts = path.split("/");
            if (parts.length >= 1 && StringUtils.hasText(parts[0])) {
                return parts[0].trim();
            }
        }
        throw new IllegalArgumentException("organization is required");
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
