package com.app.carimbai.repositories;

import com.app.carimbai.models.Stamp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StampRepository extends JpaRepository<Stamp, Long> {
}
