package com.app.carimbai.repositories;

import com.app.carimbai.models.Program;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramRepository extends JpaRepository<Program, Long> {
}
