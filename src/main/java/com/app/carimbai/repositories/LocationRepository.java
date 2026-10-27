package com.app.carimbai.repositories;

import com.app.carimbai.models.core.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.domain.Pageable;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByMerchantId(Long merchantId);
    List<Location> findByMerchantIdOrderByIdAsc(Long merchantId, Pageable pageable);

}
