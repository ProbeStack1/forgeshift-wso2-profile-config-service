package com.forgeshift.profile.config.repository;

import com.forgeshift.profile.config.domain.ProfileAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProfileAuditLogRepository extends MongoRepository<ProfileAuditLog, String> {

    Page<ProfileAuditLog> findByCompanyName(String companyName, Pageable pageable);

    Page<ProfileAuditLog> findByCompanyNameAndProvider(String companyName, String provider, Pageable pageable);
}
