package com.forgeshift.profile.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KongKonnectVerifyResponse {

    private String companyName;
    private String adminUrl;
    private String region;
    private List<ControlPlaneInfo> controlPlanes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ControlPlaneInfo {
        private String id;
        private String name;
    }
}
