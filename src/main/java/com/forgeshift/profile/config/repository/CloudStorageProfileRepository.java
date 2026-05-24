package com.forgeshift.profile.config.repository;

import com.forgeshift.profile.config.domain.CloudStorageProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CloudStorageProfileRepository extends MongoRepository<CloudStorageProfile, String> {

    Optional<CloudStorageProfile> findByCompanyNameAndProfileName(String companyName, String profileName);

    List<CloudStorageProfile> findByCompanyName(String companyName);
}
