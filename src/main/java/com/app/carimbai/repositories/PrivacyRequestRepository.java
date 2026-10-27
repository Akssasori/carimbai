package com.app.carimbai.repositories;

import com.app.carimbai.enums.*;
import com.app.carimbai.models.PrivacyRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface PrivacyRequestRepository extends JpaRepository<PrivacyRequest,UUID> {
    List<PrivacyRequest> findBySubjectTypeAndSubjectKeyOrderByCreatedAtDesc(
            AuditActorType type,UUID key,Pageable page);
    List<PrivacyRequest> findAllByOrderByCreatedAtAsc(Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PrivacyRequest r where r.id=:id")
    Optional<PrivacyRequest> findForUpdate(UUID id);
}
