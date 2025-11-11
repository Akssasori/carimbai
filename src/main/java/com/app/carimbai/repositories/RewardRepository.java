package com.app.carimbai.repositories;

import com.app.carimbai.models.fidelity.Reward;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardRepository extends JpaRepository<Reward, Long> {
}
