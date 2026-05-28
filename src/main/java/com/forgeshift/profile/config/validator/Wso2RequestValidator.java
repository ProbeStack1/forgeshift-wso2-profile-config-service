package com.forgeshift.profile.config.validator;

import com.forgeshift.profile.config.domain.Wso2Profile;
import com.forgeshift.profile.config.dto.Wso2ProfileInfoRequest;
import com.forgeshift.profile.config.dto.Wso2ProfileRequest;
import com.forgeshift.profile.config.dto.Wso2VerifyRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class Wso2RequestValidator {

    private static final int MAX_COMPANY_NAME_LENGTH = 100;
    private static final int MAX_PROFILE_NAME_LENGTH = 64;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PROFILE_NAME_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9][a-zA-Z0-9_-]{0,62}[a-zA-Z0-9]$|^[a-zA-Z0-9]$");
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "INACTIVE", "SUSPENDED");

    public void validateInfoRequest(Wso2ProfileInfoRequest request) {
        requireRequest(request);
        request.setCompanyName(normalizeCompanyName(request.getCompanyName()));
        request.setProfileName(normalizeOptionalProfileName(request.getProfileName()));
        request.setWso2BaseUrl(normalizeUrl(request.getWso2BaseUrl(), "wso2BaseUrl"));
        request.setUsername(requiredTrimmed(request.getUsername(), "username"));
        request.setPassword(requiredTrimmed(request.getPassword(), "password"));
        normalizeClientCredentials(request.getClientId(), request.getClientSecret());
        request.setClientId(normalizeOptional(request.getClientId()));
        request.setClientSecret(normalizeOptional(request.getClientSecret()));
        request.setUserEmail(normalizeOptionalEmail(request.getUserEmail()));
        request.setNotes(normalizeOptional(request.getNotes()));
    }

    public void validateCreateRequest(Wso2ProfileRequest request) {
        requireRequest(request);
        normalizeProfileRequest(request);
        request.setDefaultWso2Tenant(requiredTrimmed(request.getDefaultWso2Tenant(), "defaultWso2Tenant"));
        request.setStatus(normalizeStatus(request.getStatus()));
    }

    public void validateUpdateRequest(Wso2ProfileRequest request) {
        requireRequest(request);
        normalizeProfileRequest(request);
        request.setDefaultWso2Tenant(normalizeOptional(request.getDefaultWso2Tenant()));
        request.setStatus(normalizeStatus(request.getStatus()));
    }

    public void validateVerifyRequest(Wso2VerifyRequest request) {
        requireRequest(request);
        request.setWso2BaseUrl(normalizeUrl(request.getWso2BaseUrl(), "wso2BaseUrl"));
        request.setUsername(requiredTrimmed(request.getUsername(), "username"));
        request.setPassword(requiredTrimmed(request.getPassword(), "password"));
        request.setCompanyName(normalizeOptionalCompanyName(request.getCompanyName()));
        request.setProfileName(normalizeOptionalProfileName(request.getProfileName()));
        normalizeClientCredentials(request.getClientId(), request.getClientSecret());
        request.setClientId(normalizeOptional(request.getClientId()));
        request.setClientSecret(normalizeOptional(request.getClientSecret()));
    }

    public String normalizeRequiredCompanyName(String companyName) {
        return normalizeCompanyName(companyName);
    }

    public String normalizeRequiredProfileName(String profileName) {
        return normalizeProfileName(profileName);
    }

    public String normalizeOptionalTenant(String tenant) {
        return normalizeOptional(tenant);
    }

    public void validateUniqueWso2Config(List<Wso2Profile> profiles,
                                         String wso2BaseUrl,
                                         String defaultWso2Tenant,
                                         String currentProfileId) {
        if (profiles == null || profiles.isEmpty()) {
            return;
        }

        String normalizedUrl = normalizeUrl(wso2BaseUrl, "wso2BaseUrl");
        String normalizedTenant = requiredTrimmed(defaultWso2Tenant, "defaultWso2Tenant");
        for (Wso2Profile profile : profiles) {
            if (profile == null || !isActive(profile)) {
                continue;
            }
            if (StringUtils.hasText(currentProfileId) && currentProfileId.equals(profile.getId())) {
                continue;
            }
            String existingUrl = normalizeStoredUrl(profile.getWso2BaseUrl());
            String existingTenant = normalizeOptional(profile.getDefaultWso2Tenant());
            if (existingUrl != null
                    && normalizedUrl.equalsIgnoreCase(existingUrl)
                    && normalizedTenant.equals(existingTenant)) {
                throw new IllegalStateException(
                        "Active WSO2 profile already exists for this company, wso2BaseUrl, and defaultWso2Tenant: "
                                + profile.getId());
            }
        }
    }

    private void normalizeProfileRequest(Wso2ProfileRequest request) {
        request.setCompanyName(normalizeCompanyName(request.getCompanyName()));
        request.setProfileName(normalizeProfileName(request.getProfileName()));
        request.setWso2BaseUrl(normalizeUrl(request.getWso2BaseUrl(), "wso2BaseUrl"));
        request.setUsername(requiredTrimmed(request.getUsername(), "username"));
        request.setPassword(requiredTrimmed(request.getPassword(), "password"));
        normalizeClientCredentials(request.getClientId(), request.getClientSecret());
        request.setClientId(normalizeOptional(request.getClientId()));
        request.setClientSecret(normalizeOptional(request.getClientSecret()));
        request.setUserEmail(normalizeOptionalEmail(request.getUserEmail()));
        request.setNotes(normalizeOptional(request.getNotes()));
    }

    private void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
    }

    private String normalizeCompanyName(String companyName) {
        String value = requiredTrimmed(companyName, "companyName").toLowerCase(Locale.ROOT);
        if (value.length() > MAX_COMPANY_NAME_LENGTH) {
            throw new IllegalArgumentException("companyName exceeds maximum length of " + MAX_COMPANY_NAME_LENGTH);
        }
        return value;
    }

    private String normalizeOptionalCompanyName(String companyName) {
        if (!StringUtils.hasText(companyName)) {
            return null;
        }
        return normalizeCompanyName(companyName);
    }

    private String normalizeProfileName(String profileName) {
        String value = requiredTrimmed(profileName, "profileName");
        validateProfileNameFormat(value);
        return value;
    }

    private String normalizeOptionalProfileName(String profileName) {
        if (!StringUtils.hasText(profileName)) {
            return null;
        }
        String value = profileName.trim();
        validateProfileNameFormat(value);
        return value;
    }

    private void validateProfileNameFormat(String profileName) {
        if (profileName.length() > MAX_PROFILE_NAME_LENGTH) {
            throw new IllegalArgumentException("profileName exceeds maximum length of " + MAX_PROFILE_NAME_LENGTH);
        }
        if (!PROFILE_NAME_PATTERN.matcher(profileName).matches()) {
            throw new IllegalArgumentException(
                    "profileName must contain only alphanumeric characters, hyphens, and underscores");
        }
    }

    private String normalizeUrl(String url, String fieldName) {
        String value = requiredTrimmed(url, fieldName);
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new IllegalArgumentException(fieldName + " must be a valid HTTP/HTTPS URL");
        }
        while (value.endsWith("/") && value.length() > "https://".length()) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeStoredUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String value = url.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return null;
        }
        while (value.endsWith("/") && value.length() > "https://".length()) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeOptionalEmail(String email) {
        String value = normalizeOptional(email);
        if (value == null) {
            return null;
        }
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("userEmail must be a valid email address");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        String value = normalizeOptional(status);
        if (value == null) {
            return null;
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(value)) {
            throw new IllegalArgumentException("status must be one of ACTIVE, INACTIVE, or SUSPENDED");
        }
        return value;
    }

    private void normalizeClientCredentials(String clientId, String clientSecret) {
        boolean hasClientId = StringUtils.hasText(clientId);
        boolean hasClientSecret = StringUtils.hasText(clientSecret);
        if (hasClientId != hasClientSecret) {
            throw new IllegalArgumentException("clientId and clientSecret must be provided together");
        }
    }

    private String requiredTrimmed(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isActive(Wso2Profile profile) {
        String status = profile.getStatus();
        return !StringUtils.hasText(status) || "ACTIVE".equalsIgnoreCase(status);
    }
}
