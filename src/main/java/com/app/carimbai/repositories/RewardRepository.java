package com.app.carimbai.repositories;

import com.app.carimbai.models.fidelity.Reward;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface RewardRepository extends JpaRepository<Reward, Long> {

    @Query("""
      select count(r)
      from Reward r
      where r.card.program.merchant.id = :merchantId
        and r.issuedAt >= :since
    """)
    long countByMerchantSince(Long merchantId, OffsetDateTime since);

    @Query("""
      select r from Reward r
      join fetch r.card c
      join fetch c.program p
      join fetch c.customer
      left join fetch r.cashier
      left join fetch r.location
      where p.merchant.id = :merchantId
      order by r.issuedAt desc
    """)
    List<Reward> findRecentByMerchant(Long merchantId, Pageable pageable);
}
