package com.forgeshift.profile.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-body request for {@code POST /cloud-storage/profiles}.
 *
 * For multipart uploads (uploading the SA JSON file directly), the controller
 * accepts a {@code POST /cloud-storage/profiles/upload} variant that takes
 * form fields + a file part.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudStorageProfileRequest {

    @NotBlank
    @Schema(description = "Multi-tenancy partner id", example = "probestack",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String companyName;

    @NotBlank
    @Schema(description = "Profile name", example = "primary",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String profileName;

    @NotBlank
    @Schema(description = "Target GCS bucket name", example = "forgeshift-staging",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String bucket;

    /**
     * Full GCP service-account JSON, base64-encoded.
     * Operators uploading the file directly should use the
     * {@code /cloud-storage/profiles/upload} multipart endpoint instead.
     */
    @NotBlank
    @Schema(description = "Base64-encoded GCP service-account JSON",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String serviceAccountJsonBase64;

    @Schema(description = "Optional path prefix inside the bucket")
    private String objectPrefix;

    private String notes;
    private String userEmail;
}
