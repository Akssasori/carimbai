package com.app.carimbai.repositories;

import com.app.carimbai.models.fidelity.Stamp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;

public interface StampRepository extends JpaRepository<Stamp, Long> {

    @Query("""
      select case when count(s)>0 then true else false end
      from Stamp s
      where s.card.id = :cardId
        and (:locationId is null or s.location.id = :locationId)
        and s.whenAt > :since
    """)
    boolean existsRecentByCardAndLocation(Long cardId, Long locationId, OffsetDateTime since);
}
