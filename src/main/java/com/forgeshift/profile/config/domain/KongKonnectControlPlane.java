package com.forgeshift.profile.config.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KongKonnectControlPlane {

    private String controlPlaneId;
    private String controlPlaneName;
}
