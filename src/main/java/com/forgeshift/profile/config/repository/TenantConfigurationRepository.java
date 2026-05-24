package com.forgeshift.profile.config.repository;

import com.forgeshift.profile.config.domain.TenantConfiguration;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TenantConfigurationRepository extends MongoRepository<TenantConfiguration, String> {

    Optional<TenantConfiguration> findByTenantId(String tenantId);
}
