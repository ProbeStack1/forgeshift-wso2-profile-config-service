package com.forgeshift.profile.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KongKonnectVerifyRequest {

    @NotBlank
    private String companyName;

    @NotBlank
    private String adminUrl;

    @NotBlank
    private String konnectPat;

    @NotBlank
    private String region;
}
