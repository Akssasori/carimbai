package com.app.carimbai.repositories;

import com.app.carimbai.models.core.StaffUserMerchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface StaffUserMerchantRepository extends JpaRepository<StaffUserMerchant, Long> {

    Optional<StaffUserMerchant> findByStaffUserIdAndMerchantId(Long staffUserId, Long merchantId);

    @EntityGraph(attributePaths = {"staffUser", "merchant"})
    List<StaffUserMerchant> findByStaffUserIdAndActiveTrue(Long staffUserId);

    Optional<StaffUserMerchant> findByStaffUserIdAndIsDefaultTrue(Long staffUserId);

    @EntityGraph(attributePaths = {"staffUser", "merchant"})
    Optional<StaffUserMerchant> findByStaffUserIdAndMerchantIdAndActiveTrue(Long staffUserId, Long merchantId);

    @EntityGraph(attributePaths = {"staffUser", "merchant"})
    List<StaffUserMerchant> findByMerchantIdOrderByIdAsc(Long merchantId, Pageable pageable);

    @Query("select count(l) from StaffUserMerchant l where l.merchant.id = :merchantId "
            + "and l.active = true and l.staffUser.active = true and l.role = com.app.carimbai.enums.StaffRole.ADMIN")
    long countActiveAdmins(Long merchantId);

    @Query("""
      select sum from StaffUserMerchant sum
      join fetch sum.staffUser
      where sum.merchant.id = :merchantId
      order by sum.createdAt asc
    """)
    List<StaffUserMerchant> findAllByMerchantIdWithStaff(Long merchantId);
}
