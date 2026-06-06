package com.forgeshift.profile.config.repository;

import com.forgeshift.profile.config.domain.GitProfile;
import com.forgeshift.profile.config.domain.ProfileStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GitProfileRepository extends MongoRepository<GitProfile, String> {

    Optional<GitProfile> findByIdAndCompanyNameAndStatus(String id, String companyName, ProfileStatus status);

    Optional<GitProfile> findByCompanyNameAndProfileNameAndStatus(
            String companyName, String profileName, ProfileStatus status);

    List<GitProfile> findAllByCompanyNameAndStatus(String companyName, ProfileStatus status);

    boolean existsByProfileNameAndCompanyNameAndStatus(
            String profileName, String companyName, ProfileStatus status);
}
