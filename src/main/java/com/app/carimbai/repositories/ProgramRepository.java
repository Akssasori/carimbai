package com.app.carimbai.repositories;

import com.app.carimbai.models.fidelity.Program;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgramRepository extends JpaRepository<Program, Long> {

    List<Program> findByMerchantId(Long merchantId);

    List<Program> findByMerchantIdAndActiveTrue(Long merchantId);
}
