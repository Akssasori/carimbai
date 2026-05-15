package com.app.carimbai.repositories;

import com.app.carimbai.models.core.StaffUserMerchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StaffUserMerchantRepository extends JpaRepository<StaffUserMerchant, Long> {

    Optional<StaffUserMerchant> findByStaffUserIdAndMerchantId(Long staffUserId, Long merchantId);

    List<StaffUserMerchant> findByStaffUserIdAndActiveTrue(Long staffUserId);

    Optional<StaffUserMerchant> findByStaffUserIdAndIsDefaultTrue(Long staffUserId);

    Optional<StaffUserMerchant> findByStaffUserIdAndMerchantIdAndActiveTrue(Long staffUserId, Long merchantId);

    @Query("""
      select sum from StaffUserMerchant sum
      join fetch sum.staffUser
      where sum.merchant.id = :merchantId
      order by sum.createdAt asc
    """)
    List<StaffUserMerchant> findAllByMerchantIdWithStaff(Long merchantId);
}
