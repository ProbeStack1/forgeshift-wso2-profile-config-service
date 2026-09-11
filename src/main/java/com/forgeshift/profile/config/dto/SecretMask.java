package com.forgeshift.profile.config.dto;

import org.springframework.util.StringUtils;

/**
 * How a stored credential appears in a response, and how a save tells a new credential from
 * that mask coming back.
 *
 * <p>Responses never carry a stored token; they carry {@link #MASK} to say one is on file, the
 * way {@link CloudStorageProfileResponse} does for the service-account JSON. An edit form that
 * shows the mask, or its own row of bullets, and posts it back unchanged means "keep the token",
 * so a value made only of mask characters is never saved as one.</p>
 */
public final class SecretMask {

    /** Stands in for a stored credential in every response. */
    public static final String MASK = "***";

    /** U+2022, the dot a password field shows. */
    private static final char BULLET = 0x2022;

    private SecretMask() {
    }

    /** {@link #MASK} when a credential is stored, otherwise null, which leaves the field out of the JSON. */
    public static String of(String stored) {
        return StringUtils.hasText(stored) ? MASK : null;
    }

    /**
     * Whether a submitted value is a credential to save. Blank, or nothing but {@code *} and
     * {@code •} - this service's mask, or the {@code ••••••••} the legacy Git config page puts in
     * its token field - is not: no Konnect or GitHub token looks like that.
     */
    public static boolean isNewSecret(String submitted) {
        if (!StringUtils.hasText(submitted)) {
            return false;
        }
        return !submitted.strip().chars().allMatch(c -> c == '*' || c == BULLET);
    }
}
