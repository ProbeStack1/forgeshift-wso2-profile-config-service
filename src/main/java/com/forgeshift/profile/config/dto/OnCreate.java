package com.forgeshift.profile.config.dto;

import jakarta.validation.groups.Default;

/**
 * Validation group for a profile's first save. It extends {@link Default}, so validating it
 * checks every ordinary constraint too, plus the ones only a create has: the token, which an
 * update may leave out to keep the stored one.
 */
public interface OnCreate extends Default {
}
