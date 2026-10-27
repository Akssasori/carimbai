package com.app.carimbai.repositories;

import com.app.carimbai.models.fidelity.Program;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.domain.Pageable;

public interface ProgramRepository extends JpaRepository<Program, Long> {

    List<Program> findByMerchantId(Long merchantId);
    List<Program> findByMerchantIdOrderByIdAsc(Long merchantId, Pageable pageable);

    List<Program> findByMerchantIdAndActiveTrue(Long merchantId);
}
