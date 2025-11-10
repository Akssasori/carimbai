package com.app.carimbai.repositories;

import com.app.carimbai.models.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByMerchantId(Long merchantId);

}
