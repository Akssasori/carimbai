package com.app.carimbai.repositories;

import com.app.carimbai.models.core.StaffUserMerchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffUserMerchantRepository extends JpaRepository<StaffUserMerchant, Long> {

    Optional<StaffUserMerchant> findByStaffUserIdAndMerchantId(Long staffUserId, Long merchantId);

    List<StaffUserMerchant> findByStaffUserIdAndActiveTrue(Long staffUserId);

    Optional<StaffUserMerchant> findByStaffUserIdAndIsDefaultTrue(Long staffUserId);

    Optional<StaffUserMerchant> findByStaffUserIdAndMerchantIdAndActiveTrue(Long staffUserId, Long merchantId);
}
