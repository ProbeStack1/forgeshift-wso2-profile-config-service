package com.forgeshift.profile.config.repository;

import com.forgeshift.profile.config.domain.Wso2Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface Wso2ProfileRepository extends MongoRepository<Wso2Profile, String> {

    Optional<Wso2Profile> findByCompanyNameAndWso2TenantAndProfileName(
            String companyName, String wso2Tenant, String profileName);

    List<Wso2Profile> findByCompanyName(String companyName);

    List<Wso2Profile> findByCompanyNameAndWso2Tenant(String companyName, String wso2Tenant);
}
