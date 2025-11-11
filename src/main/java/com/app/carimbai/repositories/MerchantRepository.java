package com.app.carimbai.repositories;

import com.app.carimbai.models.core.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {
}
