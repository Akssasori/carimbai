package com.app.carimbai.repositories;

import com.app.carimbai.models.core.StaffUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {
}
