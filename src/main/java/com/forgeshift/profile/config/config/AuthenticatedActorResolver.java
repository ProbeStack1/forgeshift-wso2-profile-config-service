package com.forgeshift.profile.config.config;

import com.forge.security.authn.model.AuthnToken;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Who the caller is, taken from the VERIFIED token rather than from what they said.
 *
 * <p>This is the point of adding authentication at all. Until now this service
 * knew its caller only through the {@code X-Partner-Id} tenant header, which the
 * client sets and nobody checks - and this is the service that holds every WSO2,
 * Kong Konnect and cloud-storage credential the migration uses. These claims come
 * off a token whose signature this service checked against the platform JWKS, so
 * they cannot be chosen by the caller.</p>
 *
 * <p>Ported from ps-community-svc. The claim FALLBACKS are its, and they are not
 * decoration: tokens minted by different parts of the platform spell the same idea
 * differently ({@code organization_id} / {@code userOrgId} / {@code backendOrgId}),
 * and dropping the alternatives would reject callers this accepts today.</p>
 *
 * <p>Nothing calls this yet. It lands with the filters so the six w2k services keep
 * the same shape, and so the tenant can move from the header onto the token's
 * organisation claim as a small change rather than a new one.</p>
 */
@Component
public class AuthenticatedActorResolver {

    /** The organisation the caller belongs to. 403 when the token carries none. */
    public String requireOrganizationId() {
        AuthnToken token = requireToken();
        String organizationId = firstText(
                stringClaim(token, "organization_id"),
                stringClaim(token, "userOrgId"),
                stringClaim(token, "backendOrgId"));
        if (!StringUtils.hasText(organizationId)) {
            throw forbidden("Authenticated token must contain an organization_id claim");
        }
        return organizationId;
    }

    public AuthenticatedActor requireActor() {
        AuthnToken token = requireToken();
        String userId = firstText(token.getSubject(), stringClaim(token, "userId"), stringClaim(token, "admin_id"));
        String email = firstText(stringClaim(token, "email"), stringClaim(token, "userEmail"));
        String name = firstText(stringClaim(token, "name"), stringClaim(token, "userName"), email, userId, "User");
        String role = firstText(stringClaim(token, "role"), stringClaim(token, "userRole"), "USER");
        if (!StringUtils.hasText(userId) && !StringUtils.hasText(email)) {
            throw forbidden("Authenticated token must contain a user identity");
        }
        return new AuthenticatedActor(trimToNull(userId), trimToNull(email), trimToNull(name), role);
    }

    private AuthnToken requireToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof AuthnToken token)) {
            throw forbidden("A validated authentication token is required");
        }
        return token;
    }

    /*
     * A plain 403. ps-community-svc throws its own ForbiddenOperationException;
     * this service's exception package is about profile validation, and a
     * single call site does not justify a new type there.
     */
    private ResponseStatusException forbidden(String reason) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
    }

    private String stringClaim(AuthnToken token, String name) {
        Object value = token.getClaim(name);
        return value instanceof String text ? text : null;
    }

    private String firstText(String... values) {
        for (String value : values) if (StringUtils.hasText(value)) return value.trim();
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record AuthenticatedActor(String userId, String email, String name, String role) {
    }
}
