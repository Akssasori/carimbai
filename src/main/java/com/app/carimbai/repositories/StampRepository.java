package com.app.carimbai.repositories;

import com.app.carimbai.models.fidelity.Stamp;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface StampRepository extends JpaRepository<Stamp, Long> {

    @Query("""
      select case when count(s)>0 then true else false end
      from Stamp s
      where s.card.id = :cardId
        and s.whenAt > :since
    """)
    boolean existsRecentByCard(Long cardId, OffsetDateTime since);

    @Query("""
      select count(s)
      from Stamp s
      where s.card.program.merchant.id = :merchantId
        and s.whenAt >= :since
    """)
    long countByMerchantSince(Long merchantId, OffsetDateTime since);

    @Query("""
      select s from Stamp s
      join fetch s.card c
      join fetch c.program p
      join fetch c.customer
      left join fetch s.cashier
      left join fetch s.location
      where p.merchant.id = :merchantId
      order by s.whenAt desc
    """)
    List<Stamp> findRecentByMerchant(Long merchantId, Pageable pageable);
}
