package com.forgeshift.profile.config.repository;

import com.forgeshift.profile.config.domain.Wso2Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface Wso2ProfileRepository extends MongoRepository<Wso2Profile, String> {

    Optional<Wso2Profile> findByCompanyNameAndProfileName(String companyName, String profileName);

    List<Wso2Profile> findByCompanyName(String companyName);

    /** Look up by the single tenant this profile manages (exact match). */
    List<Wso2Profile> findByCompanyNameAndDefaultWso2Tenant(String companyName, String defaultWso2Tenant);
}
