package com.forgeshift.profile.config.repository;

import com.forgeshift.profile.config.domain.KongKonnectProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface KongKonnectProfileRepository extends MongoRepository<KongKonnectProfile, String> {

    Optional<KongKonnectProfile> findByCompanyNameAndProfileName(String companyName, String profileName);

    List<KongKonnectProfile> findByCompanyName(String companyName);
}
